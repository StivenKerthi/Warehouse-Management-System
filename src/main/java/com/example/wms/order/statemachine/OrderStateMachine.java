package com.example.wms.order.statemachine;

import com.example.wms.common.exception.BusinessException;
import com.example.wms.common.exception.StateMachineException;
import com.example.wms.order.model.Order;
import com.example.wms.order.model.OrderAuditLog;
import com.example.wms.order.model.OrderStatus;
import com.example.wms.order.service.OrderAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.example.wms.order.model.OrderStatus.*;

/**
 * Single source of truth for all order status transitions.
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li>This is the <strong>only</strong> place that calls {@code order.setStatus()}.
 *       Service or controller code must never set status directly.</li>
 *   <li>Every transition delegates audit log writes to {@link OrderAuditService#log},
 *       which participates in the same transaction — guaranteeing atomicity.</li>
 *   <li>An illegal transition throws {@link StateMachineException} (HTTP 409).</li>
 *   <li>Every transition evicts the {@code sla-report} Redis cache so reports
 *       never show stale data.</li>
 * </ul>
 *
 * <h2>Valid transitions</h2>
 * <pre>
 * CREATED            → AWAITING_APPROVAL, CANCELED
 * AWAITING_APPROVAL  → APPROVED, DECLINED
 * APPROVED           → UNDER_DELIVERY, CANCELED
 * DECLINED           → AWAITING_APPROVAL, CANCELED
 * UNDER_DELIVERY     → FULFILLED           (cron job only)
 * FULFILLED          → (terminal)
 * CANCELED           → (terminal)
 * </pre>
 *
 * <h2>Side effects per transition</h2>
 * <ul>
 *   <li>{@code * → DECLINED}: sets {@code order.declineReason} (required, non-blank).</li>
 *   <li>{@code * → !DECLINED}: clears {@code order.declineReason} (DB CHECK constraint
 *       requires {@code decline_reason IS NULL} when {@code status != 'DECLINED'}).</li>
 *   <li>{@code * → AWAITING_APPROVAL}: sets {@code order.submittedAt} to UTC now.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateMachine {

    private final OrderAuditService auditService;

    // -------------------------------------------------------------------------
    // Transition map
    // -------------------------------------------------------------------------

    /**
     * Complete, authoritative map of all valid transitions.
     * Terminal states (FULFILLED, CANCELED) map to an empty set.
     * Backed by EnumMap + EnumSet for O(1) lookup with zero boxing.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS;

    static {
        Map<OrderStatus, Set<OrderStatus>> m = new EnumMap<>(OrderStatus.class);
        m.put(CREATED,           EnumSet.of(AWAITING_APPROVAL, CANCELED));
        m.put(AWAITING_APPROVAL, EnumSet.of(APPROVED, DECLINED));
        m.put(APPROVED,          EnumSet.of(UNDER_DELIVERY, CANCELED));
        m.put(DECLINED,          EnumSet.of(AWAITING_APPROVAL, CANCELED));
        m.put(UNDER_DELIVERY,    EnumSet.of(FULFILLED));
        m.put(FULFILLED,         EnumSet.noneOf(OrderStatus.class));
        m.put(CANCELED,          EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS = Collections.unmodifiableMap(m);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Validates and applies a state transition to the given order.
     *
     * <p>On success:
     * <ol>
     *   <li>Updates {@code order.status} and applies the relevant side effects
     *       (see class-level Javadoc).</li>
     *   <li>Delegates to {@link OrderAuditService#log} — writes an {@link OrderAuditLog}
     *       entry in the caller's transaction.</li>
     *   <li>Evicts the {@code sla-report} Redis cache.</li>
     * </ol>
     *
     * <p><strong>Must be called within an active transaction.</strong> Propagation is
     * {@link Propagation#MANDATORY} — this method will throw
     * {@link org.springframework.transaction.IllegalTransactionStateException} if no
     * transaction is active, preventing audit log writes from being silently skipped.
     *
     * @param order         the managed JPA entity to mutate (must be attached to the session)
     * @param newStatus     the target status
     * @param changedBy     username of the actor triggering the transition
     * @param declineReason required (non-blank) when {@code newStatus == DECLINED}; null otherwise
     * @throws StateMachineException if the transition is not in the valid transition map (HTTP 409)
     * @throws BusinessException     if transitioning to DECLINED without a decline reason (HTTP 422)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @CacheEvict(cacheNames = "sla-report", allEntries = true)
    public void transition(Order order, OrderStatus newStatus, String changedBy,
                           @Nullable String declineReason) {

        OrderStatus currentStatus = order.getStatus();

        // 1. Guard: validate the transition is legal
        Set<OrderStatus> allowed = TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new StateMachineException(
                    "INVALID_TRANSITION",
                    String.format("Order '%s' cannot transition from %s to %s. " +
                                  "Allowed from %s: %s",
                            order.getOrderNumber(), currentStatus, newStatus,
                            currentStatus, allowed.isEmpty() ? "none (terminal state)" : allowed));
        }

        // 2. Guard: decline reason is mandatory when declining
        if (newStatus == DECLINED && (declineReason == null || declineReason.isBlank())) {
            throw new BusinessException(
                    "DECLINE_REASON_REQUIRED",
                    "A decline reason must be provided when declining an order.");
        }

        // 3. Apply side effects in dependency order

        // 3a. Status — must be set before any other side effect so that the DB
        //     CHECK constraint on decline_reason is satisfied when we set/clear it.
        order.setStatus(newStatus);

        // 3b. Decline reason — DB CHECK: decline_reason IS NULL OR status = 'DECLINED'
        if (newStatus == DECLINED) {
            order.setDeclineReason(declineReason);
        } else {
            // Clears decline_reason when leaving DECLINED state (or transitioning to
            // any non-DECLINED state) to satisfy the DB CHECK constraint.
            order.setDeclineReason(null);
        }

        // 3c. Submitted timestamp — set each time the order is (re-)submitted
        if (newStatus == AWAITING_APPROVAL) {
            order.setSubmittedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }

        // 4. Delegate audit log write — same transaction via MANDATORY propagation
        auditService.log(order, currentStatus, newStatus, changedBy);

        log.info("Order '{}' transitioned {} → {} by '{}'",
                order.getOrderNumber(), currentStatus, newStatus, changedBy);
    }

    /**
     * Writes the initial {@code null → CREATED} audit entry for a newly persisted order.
     *
     * <p>Called once by {@code OrderService.createOrder()} immediately after the order
     * is first saved. The order's CREATED status is set via {@code @PrePersist} on the
     * entity — not through {@link #transition} — because there is no "from" state.
     * This method ensures that initial creation is still recorded in the audit trail,
     * matching the V7 migration's allowance for {@code previous_status IS NULL}.
     *
     * <p><strong>Must be called within an active transaction.</strong>
     *
     * @param order     the managed JPA entity (must already be persisted with CREATED status)
     * @param changedBy username of the client who created the order
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(Order order, String changedBy) {
        auditService.log(order, null, CREATED, changedBy);
        log.info("Order '{}' created by '{}'", order.getOrderNumber(), changedBy);
    }
}
