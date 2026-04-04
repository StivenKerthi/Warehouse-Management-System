package com.example.wms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT issued after successful authentication")
public record LoginResponse(

        @Schema(description = "Signed HS256 JWT — include as 'Authorization: Bearer <token>'")
        String token,

        @Schema(description = "Token type, always 'Bearer'", example = "Bearer")
        String tokenType,

        @Schema(description = "Authenticated username", example = "warehouse_mgr")
        String username,

        @Schema(description = "User role", example = "WAREHOUSE_MANAGER")
        String role,

        @Schema(description = "Token lifetime in seconds", example = "86400")
        long expiresIn
) {}
