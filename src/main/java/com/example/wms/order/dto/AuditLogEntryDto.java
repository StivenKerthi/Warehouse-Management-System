package com.example.wms.order.dto;

import com.example.wms.order.model.OrderStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only projection of a single {@code order_audit_log} row.
 *
 * <p>{@code previousStatus} is {@code null} for the very first entry
 * (the {@code null → CREATED} initialisation entry written when an order is created).
 */
public record AuditLogEntryDto(
        UUID id,
        OrderStatus previousStatus,   // nullable — null on the initial CREATED entry
        OrderStatus newStatus,
        String changedBy,
        OffsetDateTime changedAt
) {}
