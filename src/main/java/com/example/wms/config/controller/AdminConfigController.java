package com.example.wms.config.controller;

import com.example.wms.common.dto.ApiResponse;
import com.example.wms.config.dto.SystemConfigDto;
import com.example.wms.config.dto.UpdateConfigRequest;
import com.example.wms.config.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Runtime configuration management — SYSTEM_ADMIN role only.
 *
 * <p>URL-level protection ({@code /api/admin/**} → {@code SYSTEM_ADMIN}) is
 * enforced by {@code SecurityConfig}.
 *
 * <p>Config keys are owned by Flyway migrations. The API allows updating
 * existing values only — it will return 404 for unknown keys.
 */
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@Tag(name = "Admin — Config", description = "System configuration endpoints (SYSTEM_ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
public class AdminConfigController {

    private final SystemConfigService configService;

    @GetMapping
    @Operation(summary = "List all configuration entries")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ApiResponse<List<SystemConfigDto>> getConfig() {
        return ApiResponse.of(configService.findAll());
    }

    @PutMapping
    @Operation(
        summary = "Update a configuration value",
        description = """
            Updates an existing config key. Known keys:
            - `delivery_window_days` — number of calendar days ahead a delivery can be scheduled (max 30, default 14)
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Config key not found")
    })
    public ApiResponse<SystemConfigDto> updateConfig(@Valid @RequestBody UpdateConfigRequest request) {
        return ApiResponse.of(configService.update(request));
    }
}
