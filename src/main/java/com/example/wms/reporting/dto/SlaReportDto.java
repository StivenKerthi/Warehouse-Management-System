package com.example.wms.reporting.dto;

import com.example.wms.order.model.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Full SLA report payload returned by {@code GET /api/admin/reports/sla}.
 *
 * <p>All four sections are computed in a single service call and cached together
 * in Redis under the {@code sla-report} cache (5-min TTL, evicted on any order
 * status change).
 *
 * @param orderCountsByStatus   order count for every {@link OrderStatus} value;
 *                              statuses with zero orders are included with value {@code 0}
 * @param avgFulfilmentHours    average elapsed hours from {@code AWAITING_APPROVAL}
 *                              to {@code FULFILLED}; {@code null} when no fulfilled
 *                              orders exist in the requested period
 * @param recentlyFulfilled     the 5 most recently fulfilled orders, newest first
 * @param activeTruckCount      number of trucks where {@code active = true}
 * @param totalContainerVolume  sum of {@code container_volume} for all active trucks (m³)
 */
public record SlaReportDto(
        Map<OrderStatus, Long> orderCountsByStatus,
        Double avgFulfilmentHours,
        List<RecentFulfilmentDto> recentlyFulfilled,
        long activeTruckCount,
        BigDecimal totalContainerVolume
) {}
