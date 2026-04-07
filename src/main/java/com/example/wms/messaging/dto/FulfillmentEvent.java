package com.example.wms.messaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event payload published to {@code warehouse.order.fulfilled}.
 *
 * <p>Schema is fixed — do not add fields without a migration plan, as
 * consumers (and the DLQ) may have older messages in flight.
 *
 * <p>Uses a plain class (not a record) so Jackson can deserialise it
 * without requiring a {@code @JsonCreator} or all-args constructor on
 * the consumer side.
 */
public class FulfillmentEvent {

    private UUID orderId;
    private String clientUsername;
    private List<ItemLine> items;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    // ── Jackson no-arg constructor ────────────────────────────────────────────

    public FulfillmentEvent() {}

    // ── All-args constructor (used by the producer) ───────────────────────────

    public FulfillmentEvent(UUID orderId,
                            String clientUsername,
                            List<ItemLine> items,
                            LocalDate deliveryDate) {
        this.orderId        = orderId;
        this.clientUsername = clientUsername;
        this.items          = items;
        this.deliveryDate   = deliveryDate;
    }

    // ── Getters / setters (required for Jackson deserialisation) ─────────────

    public UUID getOrderId()                { return orderId; }
    public void setOrderId(UUID orderId)    { this.orderId = orderId; }

    public String getClientUsername()                   { return clientUsername; }
    public void setClientUsername(String clientUsername){ this.clientUsername = clientUsername; }

    public List<ItemLine> getItems()                { return items; }
    public void setItems(List<ItemLine> items)      { this.items = items; }

    public LocalDate getDeliveryDate()                  { return deliveryDate; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }

    // ── Nested DTO ────────────────────────────────────────────────────────────

    public static class ItemLine {

        private String name;
        private int quantity;

        public ItemLine() {}

        public ItemLine(String name, int quantity) {
            this.name     = name;
            this.quantity = quantity;
        }

        public String getName()             { return name; }
        public void setName(String name)    { this.name = name; }

        public int getQuantity()                { return quantity; }
        public void setQuantity(int quantity)   { this.quantity = quantity; }
    }
}
