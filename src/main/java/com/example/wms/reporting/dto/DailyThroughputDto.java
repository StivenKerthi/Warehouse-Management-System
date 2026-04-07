package com.example.wms.reporting.dto;

import java.time.LocalDate;

/**
 * Order submission count for a single calendar day.
 *
 * @param date            the calendar day (UTC)
 * @param submissionCount number of orders that entered {@code AWAITING_APPROVAL}
 *                        on this day; {@code 0} for days with no submissions
 */
public record DailyThroughputDto(LocalDate date, long submissionCount) {}
