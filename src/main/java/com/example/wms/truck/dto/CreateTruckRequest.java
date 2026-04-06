package com.example.wms.truck.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTruckRequest(

        @NotBlank
        @Size(max = 50)
        String chassisNumber,

        @NotBlank
        @Size(max = 20)
        String licensePlate,

        @NotNull
        @DecimalMin(value = "0.0001", message = "containerVolume must be greater than zero")
        BigDecimal containerVolume
) {}
