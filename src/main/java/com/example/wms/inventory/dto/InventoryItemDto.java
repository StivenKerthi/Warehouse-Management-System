package com.example.wms.inventory.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryItemDto(
        UUID id,
        String name,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal packageVolume,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
