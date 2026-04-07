package com.example.wms.order.repository;

import com.example.wms.order.model.OrderAuditLog;
import com.example.wms.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
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

    // -------------------------------------------------------------------------
    // Reporting — average fulfilment time (SLA report)
    // -------------------------------------------------------------------------

    /**
     * Computes the average hours from {@code AWAITING_APPROVAL} to {@code FULFILLED}
     * across all orders, with no date range filter.
     *
     * <p>Uses a self-join on {@code order_audit_log} to correlate the two timestamps
     * per order. Orders that never reached FULFILLED are excluded automatically.
     *
     * @return average hours as a {@code Double}, or {@code null} when no orders qualify
     */
    @Query(nativeQuery = true, value = """
            SELECT AVG(EXTRACT(EPOCH FROM (f.changed_at - a.changed_at)) / 3600)
            FROM   order_audit_log a
            JOIN   order_audit_log f ON a.order_id = f.order_id
            WHERE  a.new_status = 'AWAITING_APPROVAL'
              AND  f.new_status = 'FULFILLED'
            """)
    Double avgFulfilmentHours();

    /**
     * Same as {@link #avgFulfilmentHours()} but restricted to orders where the
     * {@code AWAITING_APPROVAL} entry falls within {@code [from, to)}.
     *
     * @param from inclusive lower bound (start of day UTC)
     * @param to   exclusive upper bound (start of next day UTC)
     * @return average hours, or {@code null} when no orders qualify in the range
     */
    @Query(nativeQuery = true, value = """
            SELECT AVG(EXTRACT(EPOCH FROM (f.changed_at - a.changed_at)) / 3600)
            FROM   order_audit_log a
            JOIN   order_audit_log f ON a.order_id = f.order_id
            WHERE  a.new_status = 'AWAITING_APPROVAL'
              AND  f.new_status = 'FULFILLED'
              AND  a.changed_at >= :from
              AND  a.changed_at <  :to
            """)
    Double avgFulfilmentHoursInRange(@Param("from") OffsetDateTime from,
                                     @Param("to")   OffsetDateTime to);

    // -------------------------------------------------------------------------
    // Reporting — throughput (daily order submission counts)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code [date, count]} pairs counting how many orders entered
     * {@code AWAITING_APPROVAL} on each calendar day on or after {@code since}.
     *
     * <p>Days with zero submissions are absent from the result — the service
     * fills in zeroes when building the full 30-day window.
     *
     * <p>Dates are computed in UTC to be consistent with how {@code changed_at}
     * is stored ({@code TIMESTAMPTZ} with UTC offset).
     *
     * @param since UTC timestamp of the start of the first day in the window
     * @return list of {@code [java.sql.Date, Number]} pairs ordered by date ascending
     */
    @Query(nativeQuery = true, value = """
            SELECT CAST((changed_at AT TIME ZONE 'UTC') AS date) AS submission_date,
                   COUNT(*)                                       AS submission_count
            FROM   order_audit_log
            WHERE  new_status  = 'AWAITING_APPROVAL'
              AND  changed_at >= :since
            GROUP  BY CAST((changed_at AT TIME ZONE 'UTC') AS date)
            ORDER  BY submission_date
            """)
    List<Object[]> countDailySubmissionsSince(@Param("since") OffsetDateTime since);
}
