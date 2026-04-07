package com.example.wms.delivery.scheduler;

import com.example.wms.messaging.dto.FulfillmentEvent;
import com.example.wms.order.model.Order;
import com.example.wms.order.model.OrderStatus;
import com.example.wms.order.repository.OrderRepository;
import com.example.wms.order.statemachine.OrderStateMachine;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Transactional delegate for {@link FulfillmentScheduler}.
 *
 * <p>Exists as a separate Spring bean to work around the Spring AOP
 * self-invocation limitation: calling a {@code @Transactional} method from
 * another method on the <em>same</em> bean bypasses the proxy and runs without
 * a transaction. By extracting per-order logic here, every call from the
 * scheduler goes through the AOP proxy and gets a genuine
 * {@link Propagation#REQUIRES_NEW} transaction.
 *
 * <h2>Contract with {@link FulfillmentScheduler}</h2>
 * <ol>
 *   <li>This method opens a fresh transaction, loads the order, transitions it
 *       to {@code FULFILLED} (writing the audit log in the same tx), builds the
 *       {@link FulfillmentEvent}, and commits.</li>
 *   <li>The <em>returned event is published to Kafka by the caller</em> (after
 *       this method returns, i.e. after the transaction has committed). This
 *       guarantees the consumer sees the {@code FULFILLED} DB state before
 *       processing the event.</li>
 *   <li>Any exception thrown here causes only this order's transaction to roll
 *       back. The scheduler catches the exception, logs it, and continues
 *       with the next order.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FulfillmentProcessor {

    static final String SYSTEM_USER = "fulfillment-scheduler";

    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;

    /**
     * Transitions a single order to {@code FULFILLED} within a dedicated transaction.
     *
     * <p>Loads the order with its line items and inventory details via a single
     * JOIN FETCH query (no N+1 queries). The order's {@code client} association
     * is lazy-loaded by Hibernate within the same session, adding one extra
     * SELECT at most.
     *
     * @param orderId      the order to fulfill
     * @param deliveryDate the delivery date from the {@code deliveries} table —
     *                     passed in so the caller's read-only query result is used
     *                     directly without a re-query inside this transaction
     * @return the fully built {@link FulfillmentEvent} ready to be published;
     *         never {@code null}
     * @throws EntityNotFoundException  if the order no longer exists (race condition guard)
     * @throws com.example.wms.common.exception.StateMachineException if the order is not in
     *         {@code UNDER_DELIVERY} state when the transaction runs (should not happen
     *         in normal operation — guarded here for safety)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FulfillmentEvent fulfill(UUID orderId, LocalDate deliveryDate) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found during fulfillment: " + orderId));

        // Build event data while the Hibernate session is still open.
        // Accessing order.getClient() triggers a lazy SELECT for the User entity —
        // acceptable: one extra query per order, within the same transaction.
        List<FulfillmentEvent.ItemLine> itemLines = order.getOrderItems().stream()
                .map(oi -> new FulfillmentEvent.ItemLine(
                        oi.getInventoryItem().getName(),
                        oi.getRequestedQuantity()))
                .toList();

        FulfillmentEvent event = new FulfillmentEvent(
                order.getId(),
                order.getClient().getUsername(),
                itemLines,
                deliveryDate);

        // Transition to FULFILLED — OrderStateMachine.transition() has
        // Propagation.MANDATORY and joins this REQUIRES_NEW transaction.
        // The audit log entry is written in the same transaction.
        orderStateMachine.transition(order, OrderStatus.FULFILLED, SYSTEM_USER, null);

        log.debug("Order '{}' marked FULFILLED in DB, event built for Kafka publish",
                order.getOrderNumber());

        return event;
    }
}
