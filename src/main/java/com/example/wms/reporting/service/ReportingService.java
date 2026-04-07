package com.example.wms.reporting.service;

import com.example.wms.messaging.repository.FulfilmentLogRepository;
import com.example.wms.order.model.OrderStatus;
import com.example.wms.order.repository.OrderAuditLogRepository;
import com.example.wms.order.repository.OrderRepository;
import com.example.wms.reporting.dto.DailyThroughputDto;
import com.example.wms.reporting.dto.RecentFulfilmentDto;
import com.example.wms.reporting.dto.SlaReportDto;
import com.example.wms.reporting.dto.ThroughputReportDto;
import com.example.wms.truck.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the SLA report consumed by {@code GET /api/admin/reports/sla}.
 *
 * <h2>Report sections</h2>
 * <ol>
 *   <li><strong>Order counts per status</strong> — native GROUP BY query; all 7
 *       {@link OrderStatus} values present (zero-count statuses filled in by the
 *       service to avoid gaps in the response).</li>
 *   <li><strong>Average fulfilment hours</strong> — self-join on
 *       {@code order_audit_log} correlating {@code AWAITING_APPROVAL} and
 *       {@code FULFILLED} timestamps per order.</li>
 *   <li><strong>5 most recently fulfilled orders</strong> — descending
 *       {@code fulfilled_at} from {@code fulfilment_log}.</li>
 *   <li><strong>Active truck fleet stats</strong> — count and total volume of
 *       trucks where {@code active = true}.</li>
 * </ol>
 *
 * <h2>Caching</h2>
 * Results are cached in Redis under the {@code sla-report} cache (5-min TTL).
 * The cache key encodes the {@code from}/{@code to} date parameters so that
 * different date ranges are cached independently. The cache is evicted on every
 * order status change via {@code @CacheEvict} in {@code OrderStateMachine}.
 *
 * <h2>Date range semantics</h2>
 * When provided, {@code from} and {@code to} are treated as an inclusive range
 * on calendar days (UTC). Internally they are converted to a half-open
 * {@code [fromDt, toDt)} OffsetDateTime interval:
 * <ul>
 *   <li>{@code from} → start of day in UTC</li>
 *   <li>{@code to}   → start of the next day in UTC (exclusive upper bound)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingService {

    private static final int RECENT_FULFILLED_LIMIT   = 5;
    private static final int THROUGHPUT_WINDOW_DAYS   = 30;

    private final OrderRepository            orderRepository;
    private final OrderAuditLogRepository    auditLogRepository;
    private final FulfilmentLogRepository    fulfilmentLogRepository;
    private final TruckRepository            truckRepository;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the full SLA report, optionally scoped to a date range.
     *
     * <p>Results are cached per {@code (from, to)} pair. A call with no date
     * arguments uses cache key {@code "null:null"}.
     *
     * @param from optional start date (inclusive); {@code null} means no lower bound
     * @param to   optional end date (inclusive); {@code null} means no upper bound
     */
    @Cacheable(cacheNames = "sla-report", key = "#from + ':' + #to")
    @Transactional(readOnly = true)
    public SlaReportDto getSlaReport(@Nullable LocalDate from, @Nullable LocalDate to) {
        log.debug("Building SLA report for range [{}, {}]", from, to);

        // Convert inclusive LocalDate range → half-open OffsetDateTime interval
        OffsetDateTime fromDt = (from != null) ? from.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        OffsetDateTime toDt   = (to   != null) ? to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) : null;

        boolean filtered = (fromDt != null || toDt != null);

        Map<OrderStatus, Long> counts      = buildStatusCounts(fromDt, toDt, filtered);
        Double avgHours                    = queryAvgHours(fromDt, toDt, filtered);
        List<RecentFulfilmentDto> recent   = queryRecentFulfilled();
        long activeTrucks                  = truckRepository.countActiveTrucks();
        BigDecimal totalVolume             = truckRepository.sumActiveContainerVolume();

        return new SlaReportDto(counts, avgHours, recent, activeTrucks, totalVolume);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Queries order counts grouped by status and fills in zeroes for any status
     * absent from the DB result, so the response always contains all 7 statuses.
     */
    private Map<OrderStatus, Long> buildStatusCounts(@Nullable OffsetDateTime from,
                                                     @Nullable OffsetDateTime to,
                                                     boolean filtered) {
        // Initialise all statuses to zero — ensures every status appears in the response
        Map<OrderStatus, Long> result = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            result.put(s, 0L);
        }

        List<Object[]> rows = filtered
                ? orderRepository.countByStatusGroupedInRange(from, to)
                : orderRepository.countByStatusGrouped();

        for (Object[] row : rows) {
            // row[0] = status string (native query), row[1] = count (Long or BigInteger)
            String statusName = (String) row[0];
            long   count      = ((Number) row[1]).longValue();
            try {
                result.put(OrderStatus.valueOf(statusName), count);
            } catch (IllegalArgumentException e) {
                // Defensive: skip unknown status values that may appear during migrations
                log.warn("Unrecognised order status in count query: '{}'", statusName);
            }
        }

        return result;
    }

    private Double queryAvgHours(@Nullable OffsetDateTime from,
                                 @Nullable OffsetDateTime to,
                                 boolean filtered) {
        if (filtered) {
            // When only one bound is provided, substitute a safe sentinel for the other
            OffsetDateTime effectiveFrom = (from != null) ? from : OffsetDateTime.MIN;
            OffsetDateTime effectiveTo   = (to   != null) ? to   : OffsetDateTime.MAX;
            return auditLogRepository.avgFulfilmentHoursInRange(effectiveFrom, effectiveTo);
        }
        return auditLogRepository.avgFulfilmentHours();
    }

    private List<RecentFulfilmentDto> queryRecentFulfilled() {
        return fulfilmentLogRepository.findRecentlyFulfilled(RECENT_FULFILLED_LIMIT)
                .stream()
                .map(RecentFulfilmentDto::from)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Throughput report
    // -------------------------------------------------------------------------

    /**
     * Returns daily order submission counts for the last {@value #THROUGHPUT_WINDOW_DAYS} days.
     *
     * <p>Every calendar day in the window is represented — days with no submissions
     * carry a count of {@code 0}, making the result suitable for direct charting.
     *
     * <p>The result is cached under the {@code throughput-report} cache for 5 minutes.
     * The key is a constant string since the window is always relative to today.
     */
    @Cacheable(cacheNames = "throughput-report", key = "'30d'")
    @Transactional(readOnly = true)
    public ThroughputReportDto getThroughputReport() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        // Window: [today - 29 days, today] → 30 days inclusive
        LocalDate windowStart = today.minusDays(THROUGHPUT_WINDOW_DAYS - 1);
        OffsetDateTime since  = windowStart.atStartOfDay().atOffset(ZoneOffset.UTC);

        log.debug("Building throughput report for window [{}, {}]", windowStart, today);

        List<Object[]> rows = auditLogRepository.countDailySubmissionsSince(since);

        // Index DB results by date for O(1) lookup during gap-fill
        Map<LocalDate, Long> byDate = new HashMap<>();
        for (Object[] row : rows) {
            // row[0]: java.sql.Date from PostgreSQL DATE column
            // row[1]: Number (Long / BigInteger) from COUNT(*)
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            long      count = ((Number) row[1]).longValue();
            byDate.put(date, count);
        }

        // Build the full window with zero-fill for missing days
        List<DailyThroughputDto> dailyCounts = new ArrayList<>(THROUGHPUT_WINDOW_DAYS);
        long total = 0;
        for (int i = 0; i < THROUGHPUT_WINDOW_DAYS; i++) {
            LocalDate day   = windowStart.plusDays(i);
            long      count = byDate.getOrDefault(day, 0L);
            dailyCounts.add(new DailyThroughputDto(day, count));
            total += count;
        }

        return new ThroughputReportDto(windowStart, today, dailyCounts, total);
    }
}
