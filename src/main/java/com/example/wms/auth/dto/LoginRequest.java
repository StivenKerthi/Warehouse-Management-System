package com.example.wms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials payload for login")
public record LoginRequest(

        @NotBlank(message = "username is required")
        @Schema(description = "Account username", example = "warehouse_mgr")
        String username,

        @NotBlank(message = "password is required")
        @Schema(description = "Account password", example = "s3cr3t!")
        String password
) {}
