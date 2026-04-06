package com.example.wms.order.repository;

import com.example.wms.order.model.OrderAuditLog;
import com.example.wms.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the append-only {@code order_audit_log} table.
 *
 * <p>Never delete or update rows. All writes go through
 * {@link com.example.wms.order.statemachine.OrderStateMachine}.
 */
public interface OrderAuditLogRepository extends JpaRepository<OrderAuditLog, UUID> {

    /**
     * Returns the complete audit trail for a single order, ordered by transition time.
     * Used by the client audit endpoint.
     */
    List<OrderAuditLog> findByOrderIdOrderByChangedAtAsc(UUID orderId);

    /**
     * Returns all entries for a given order that have a specific {@code newStatus}.
     * Used by the SLA reporting service to find AWAITING_APPROVAL and FULFILLED timestamps.
     */
    @Query("SELECT a FROM OrderAuditLog a WHERE a.order.id = :orderId AND a.newStatus = :status ORDER BY a.changedAt ASC")
    List<OrderAuditLog> findByOrderIdAndNewStatus(@Param("orderId") UUID orderId,
                                                   @Param("status") OrderStatus status);

    /**
     * Returns the complete audit trail for an order, but only if it is owned by the
     * given client. Returns an empty list when:
     * <ul>
     *   <li>the order does not exist, or</li>
     *   <li>the order exists but belongs to a different client.</li>
     * </ul>
     *
     * <p>Callers should treat an empty result as HTTP 404 — never reveal to a client
     * whether an order exists if they do not own it.
     */
    @Query("""
            SELECT a FROM OrderAuditLog a
            WHERE a.order.id = :orderId
              AND a.order.client.username = :username
            ORDER BY a.changedAt ASC
            """)
    List<OrderAuditLog> findByOrderIdAndClientUsername(@Param("orderId") UUID orderId,
                                                        @Param("username") String username);
}
