package com.example.wms.messaging.repository;

import com.example.wms.messaging.model.FulfilmentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the append-only {@code fulfilment_log} table.
 *
 * <h2>Access patterns</h2>
 * <ul>
 *   <li><strong>Consumer idempotency check:</strong> {@link #existsById(Object)} —
 *       read-only, runs in its own transaction (Spring Data default).
 *       The natural PK ({@code order_id}) makes this O(1).</li>
 *   <li><strong>Consumer write:</strong> {@code saveAndFlush(FulfilmentLog)} —
 *       inherited from {@link JpaRepository}. {@code saveAndFlush} issues the
 *       INSERT immediately so any PK constraint violation surfaces at the call
 *       site, not at tx commit time.</li>
 *   <li><strong>SLA report:</strong> {@link #findRecentlyFulfilled(int)} —
 *       used by {@code ReportingService} to list the N most recently fulfilled
 *       orders (TASK-044).</li>
 * </ul>
 *
 * <h2>No updates</h2>
 * This is an append-only log. The save/update paths inherited from
 * {@link JpaRepository} are only used for inserts; the entity has no
 * mutable fields after creation.
 */
public interface FulfilmentLogRepository extends JpaRepository<FulfilmentLog, UUID> {

    /**
     * Returns the {@code limit} most recently fulfilled orders, ordered by
     * {@code fulfilled_at} descending.
     *
     * <p>Used by the SLA report endpoint ({@code GET /api/admin/reports/sla})
     * to display the 5 most recent fulfilments.
     *
     * @param limit maximum number of rows to return; typically {@code 5}
     * @return list of at most {@code limit} entries, newest first; never {@code null}
     */
    @Query(value = """
            SELECT fl.*
            FROM   fulfilment_log fl
            ORDER  BY fl.fulfilled_at DESC
            LIMIT  :limit
            """,
            nativeQuery = true)
    List<FulfilmentLog> findRecentlyFulfilled(int limit);
}
