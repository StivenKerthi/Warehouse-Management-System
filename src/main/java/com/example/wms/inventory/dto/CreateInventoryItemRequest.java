package com.example.wms.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateInventoryItemRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @Min(0)
        int quantity,

        @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal unitPrice,

        @DecimalMin(value = "0.0001", message = "packageVolume must be greater than zero")
        BigDecimal packageVolume
) {}
