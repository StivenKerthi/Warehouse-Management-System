package com.example.wms.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Throughput report payload returned by {@code GET /api/admin/reports/throughput}.
 *
 * <p>Covers a fixed rolling 30-day window ending today (UTC). Every day in the
 * window is represented — days with no submissions carry a count of {@code 0},
 * making this suitable for direct charting without client-side gap-filling.
 *
 * @param from             first day of the window (inclusive, UTC)
 * @param to               last day of the window (today UTC, inclusive)
 * @param dailyCounts      one entry per calendar day in {@code [from, to]},
 *                         ordered ascending by date
 * @param totalSubmissions sum of all {@code submissionCount} values in the window
 */
public record ThroughputReportDto(
        LocalDate from,
        LocalDate to,
        List<DailyThroughputDto> dailyCounts,
        long totalSubmissions
) {}
