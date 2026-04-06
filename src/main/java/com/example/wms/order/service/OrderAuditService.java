package com.example.wms.order.service;

import com.example.wms.order.dto.AuditLogEntryDto;
import com.example.wms.order.model.Order;
import com.example.wms.order.model.OrderAuditLog;
import com.example.wms.order.model.OrderStatus;
import com.example.wms.order.repository.OrderAuditLogRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles all reads and writes on the {@code order_audit_log} table.
 *
 * <h2>Write path</h2>
 * {@link #log} is the single write entry point. It is called <strong>exclusively</strong>
 * from {@link com.example.wms.order.statemachine.OrderStateMachine} — never from service
 * or controller code directly. {@link Propagation#MANDATORY} enforces that every write
 * participates in the caller's transaction, guaranteeing audit entries are written in the
 * same transaction as the status change they record.
 *
 * <h2>Read path</h2>
 * {@link #getAuditLog} performs an ownership-checked fetch. An empty result — whether
 * because the order does not exist or belongs to a different client — always returns 404
 * to prevent clients from enumerating orders they do not own.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderAuditService {

    private final OrderAuditLogRepository auditLogRepository;

    // -------------------------------------------------------------------------
    // Write — called exclusively from OrderStateMachine
    // -------------------------------------------------------------------------

    /**
     * Appends an audit entry for a status transition.
     *
     * <p>Must be called within an active transaction (enforced by
     * {@link Propagation#MANDATORY}). The entry is flushed with the surrounding
     * transaction, ensuring atomicity with the status change on the parent order.
     *
     * @param order          the order being transitioned (managed JPA entity)
     * @param previousStatus the status before the transition; {@code null} only for
     *                       the initial {@code null → CREATED} entry
     * @param newStatus      the status the order moved into
     * @param changedBy      username of the actor who triggered the transition
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(Order order,
                    @Nullable OrderStatus previousStatus,
                    OrderStatus newStatus,
                    String changedBy) {

        auditLogRepository.save(
                OrderAuditLog.builder()
                        .order(order)
                        .previousStatus(previousStatus)
                        .newStatus(newStatus)
                        .changedBy(changedBy)
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // Read — called from ClientOrderController
    // -------------------------------------------------------------------------

    /**
     * Returns the complete audit trail for the given order, but only if it is
     * owned by {@code clientUsername}.
     *
     * <p>Returns HTTP 404 when the order does not exist <em>or</em> when it exists
     * but belongs to a different client — deliberately indistinguishable to prevent
     * clients from enumerating other clients' orders.
     *
     * @param orderId        the order to fetch the trail for
     * @param clientUsername the username extracted from the caller's JWT
     * @return audit entries ordered from oldest to newest
     * @throws EntityNotFoundException when the order is not found or not owned by this client
     */
    public List<AuditLogEntryDto> getAuditLog(UUID orderId, String clientUsername) {
        List<OrderAuditLog> entries =
                auditLogRepository.findByOrderIdAndClientUsername(orderId, clientUsername);

        if (entries.isEmpty()) {
            throw new EntityNotFoundException(
                    "Order not found: " + orderId);
        }

        return entries.stream()
                .map(e -> new AuditLogEntryDto(
                        e.getId(),
                        e.getPreviousStatus(),
                        e.getNewStatus(),
                        e.getChangedBy(),
                        e.getChangedAt()))
                .toList();
    }
}
