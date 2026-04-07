package com.example.wms.reporting.dto;

import com.example.wms.messaging.model.FulfilmentLog;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Slim projection of a {@link FulfilmentLog} row for the SLA report.
 *
 * @param orderId     the fulfilled order's UUID
 * @param client      snapshot username of the client at fulfilment time
 * @param itemCount   number of distinct line items in the order
 * @param fulfilledAt UTC timestamp when the Kafka consumer processed the event
 */
public record RecentFulfilmentDto(
        UUID orderId,
        String client,
        int itemCount,
        OffsetDateTime fulfilledAt
) {
    public static RecentFulfilmentDto from(FulfilmentLog log) {
        return new RecentFulfilmentDto(
                log.getOrderId(),
                log.getClient(),
                log.getItemCount(),
                log.getFulfilledAt()
        );
    }
}
