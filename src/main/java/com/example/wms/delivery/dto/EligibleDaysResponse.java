package com.example.wms.delivery.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Response body for {@code GET /api/manager/orders/{id}/eligible-delivery-days}.
 *
 * @param eligibleDates chronologically ordered list of valid weekday dates on which
 *                      the order can be scheduled; may be empty if no trucks have
 *                      sufficient combined capacity within the configured window
 */
public record EligibleDaysResponse(List<LocalDate> eligibleDates) {}
