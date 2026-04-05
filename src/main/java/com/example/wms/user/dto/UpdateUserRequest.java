package com.example.wms.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/admin/users/{id}}.
 *
 * <p>All fields are optional — only non-null values are applied to the existing
 * user. This allows callers to patch individual fields without resending the
 * full resource.
 */
public record UpdateUserRequest(
        @Email String email,
        @Size(min = 8, message = "Password must be at least 8 characters") String password,
        Boolean active
) {}
