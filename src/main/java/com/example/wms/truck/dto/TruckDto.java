package com.example.wms.truck.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TruckDto(
        UUID id,
        String chassisNumber,
        String licensePlate,
        BigDecimal containerVolume,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
