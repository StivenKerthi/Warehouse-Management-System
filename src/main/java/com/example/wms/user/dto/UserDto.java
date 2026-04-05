package com.example.wms.user.dto;

import com.example.wms.user.model.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only projection of a {@code User} entity returned to API consumers.
 * Password hash is intentionally excluded.
 */
public record UserDto(
        UUID           id,
        String         username,
        String         email,
        Role           role,
        boolean        active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
