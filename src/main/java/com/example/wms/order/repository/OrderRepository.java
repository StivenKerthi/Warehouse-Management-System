package com.example.wms.order.repository;

import com.example.wms.order.model.Order;
import com.example.wms.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@code orders} table.
 *
 * <h2>Query strategy</h2>
 * <ul>
 *   <li>Derived method names are used where the name remains readable.</li>
 *   <li>{@link org.springframework.data.jpa.repository.Query @Query} (JPQL or native) is
 *       used when a derived name would be excessively long or when the query spans a
 *       table whose JPA entity does not yet exist at compile time.</li>
 * </ul>
 *
 * <h2>Status-filter pattern</h2>
 * Several endpoints expose an optional {@code status} query param. Rather than a single
 * nullable-param method (which complicates JPQL), the service calls the appropriate
 * overload: {@code findByClientId} when no filter is requested, or
 * {@code findByClientIdAndStatus} when one is provided.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // -------------------------------------------------------------------------
    // CLIENT — list own orders (optional status filter)
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of all orders owned by the given client,
     * ordered by creation time descending (newest first).
     *
     * <p>Used when the client does not apply a status filter.
     *
     * @param clientId the UUID of the owning client ({@code orders.client_id})
     * @param pageable pagination + sort parameters
     */
    Page<Order> findByClientId(UUID clientId, Pageable pageable);

    /**
     * Returns a paginated, status-filtered list of orders owned by the given client.
     *
     * <p>Used when the client passes a {@code ?status=} query parameter.
     *
     * @param clientId the UUID of the owning client
     * @param status   the status to filter on
     * @param pageable pagination + sort parameters
     */
    Page<Order> findByClientIdAndStatus(UUID clientId, OrderStatus status, Pageable pageable);

    // -------------------------------------------------------------------------
    // CLIENT — single order ownership check
    // -------------------------------------------------------------------------

    /**
     * Fetches an order by its ID only if it belongs to the given client.
     *
     * <p>Returns {@link Optional#empty()} when:
     * <ul>
     *   <li>the order does not exist, or</li>
     *   <li>the order exists but is owned by a different client.</li>
     * </ul>
     *
     * <p>Callers should map an empty result to HTTP 404 — both cases are intentionally
     * indistinguishable to prevent clients from enumerating other clients' orders.
     *
     * @param id       the order UUID
     * @param clientId the UUID of the authenticated client
     */
    Optional<Order> findByIdAndClientId(UUID id, UUID clientId);

    // -------------------------------------------------------------------------
    // WAREHOUSE_MANAGER — list all orders (optional status filter)
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of all orders with the given status, ordered by
     * submission time descending so the manager sees the oldest pending orders first
     * when reversed, or the newest submissions at the top.
     *
     * <p>Use {@link JpaRepository#findAll(Pageable)} when no status filter is needed.
     *
     * @param status   the status to filter on
     * @param pageable pagination + sort parameters
     */
    Page<Order> findByStatusOrderBySubmittedAtDesc(OrderStatus status, Pageable pageable);

    // -------------------------------------------------------------------------
    // FulfillmentScheduler (cron job) — orders ready to fulfil
    // -------------------------------------------------------------------------

    /**
     * Returns all {@code UNDER_DELIVERY} orders whose scheduled delivery date is on or
     * before {@code date} (typically {@code LocalDate.now()}).
     *
     * <p>This query joins the {@code deliveries} table directly via native SQL to avoid
     * a compile-time dependency on the {@code Delivery} JPA entity, which is created in
     * a later task. The native query is equivalent to:
     * <pre>
     * SELECT o.* FROM orders o
     * JOIN   deliveries d ON d.order_id = o.id
     * WHERE  o.status      = 'UNDER_DELIVERY'
     *   AND  d.delivery_date <= :date
     * </pre>
     *
     * <p>Called exclusively by {@code FulfillmentScheduler}. Each returned order is
     * processed in its own transaction; the cron job wraps each iteration individually.
     *
     * @param date the cut-off date (inclusive); normally {@code LocalDate.now()}
     */
    @Query(
            nativeQuery = true,
            value = """
                    SELECT o.*
                    FROM   orders o
                    JOIN   deliveries d ON d.order_id = o.id
                    WHERE  o.status = 'UNDER_DELIVERY'
                      AND  d.delivery_date <= :date
                    """)
    List<Order> findReadyToFulfill(@Param("date") LocalDate date);

    // -------------------------------------------------------------------------
    // Reporting — order counts per status (SLA report)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code [status, count]} pairs for all orders, with no date filter.
     * Used by {@code ReportingService} when no date range is specified.
     */
    @Query(nativeQuery = true,
           value = "SELECT status, COUNT(*) AS cnt FROM orders GROUP BY status")
    List<Object[]> countByStatusGrouped();

    /**
     * Returns {@code [status, count]} pairs for orders created within the given
     * half-open interval {@code [from, to)}.
     *
     * @param from inclusive lower bound (start of day UTC)
     * @param to   exclusive upper bound (start of day after the requested end date)
     */
    @Query(nativeQuery = true,
           value = """
                   SELECT status, COUNT(*) AS cnt
                   FROM   orders
                   WHERE  created_at >= :from
                     AND  created_at <  :to
                   GROUP  BY status
                   """)
    List<Object[]> countByStatusGroupedInRange(@Param("from") OffsetDateTime from,
                                               @Param("to")   OffsetDateTime to);

    // -------------------------------------------------------------------------
    // Existence checks (used by OrderService to detect conflicts)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when any order for the given client is currently in the
     * specified status. Useful for lightweight guard checks without loading the entity.
     */
    boolean existsByClientIdAndStatus(UUID clientId, OrderStatus status);

    // -------------------------------------------------------------------------
    // Delivery scheduling — order + items loaded in a single query
    // -------------------------------------------------------------------------

    /**
     * Fetches an order together with its line items and each item's inventory
     * entry in a single SQL query (JOIN FETCH).
     *
     * <p>Used by {@code EligibleDayCalculator} to compute the order's total
     * required delivery volume without triggering N+1 lazy-load queries.
     * {@code DISTINCT} prevents Hibernate from returning duplicate {@code Order}
     * rows caused by the one-to-many join.
     *
     * @param id the order UUID
     * @return the order with {@code orderItems} and each item's {@code inventoryItem} initialised
     */
    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.orderItems oi
            JOIN FETCH oi.inventoryItem
            WHERE o.id = :id
            """)
    Optional<Order> findByIdWithItems(@Param("id") UUID id);
}
