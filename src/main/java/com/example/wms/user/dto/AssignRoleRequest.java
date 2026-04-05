package com.example.wms.user.dto;

import com.example.wms.user.model.Role;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(@NotNull Role role) {}
