package com.example.wms.truck.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateTruckRequest(

        @Size(max = 50)
        String chassisNumber,

        @Size(max = 20)
        String licensePlate,

        @DecimalMin(value = "0.0001", message = "containerVolume must be greater than zero")
        BigDecimal containerVolume,

        Boolean active
) {}
