package com.example.wms.config.dto;

import java.time.OffsetDateTime;

public record SystemConfigDto(
        String         key,
        String         value,
        String         updatedBy,
        OffsetDateTime updatedAt
) {}
