package com.warehouse.dto.admin;

import com.warehouse.entity.User;

import java.time.OffsetDateTime;

/**
 * Read model for the user-management screen. Exists so the {@code users} endpoints
 * can never leak credential material by accident: the entity carries a bcrypt hash
 * and DTOs are the only reliable way to keep it out of a response body.
 */
public record AdminUserDto(
        Long id,
        String username,
        String role,
        boolean active,
        OffsetDateTime createdAt
) {
    public static AdminUserDto from(User user) {
        return new AdminUserDto(
                user.getId(),
                user.getUsername(),
                user.getRole() != null ? user.getRole().name() : null,
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
