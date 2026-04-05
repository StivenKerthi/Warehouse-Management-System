package com.example.wms.config.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /api/admin/config}.
 *
 * <p>Only existing config keys can be updated — no new keys are created
 * through the API. The application returns 404 if the key is unknown.
 */
public record UpdateConfigRequest(
        @NotBlank String key,
        @NotBlank String value
) {}
