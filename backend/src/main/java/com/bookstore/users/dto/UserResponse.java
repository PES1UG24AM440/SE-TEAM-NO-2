package com.bookstore.users.dto;

import com.bookstore.users.AppUser;

import java.time.Instant;

/** Public view of an account. Deliberately has no password field. */
public record UserResponse(
    Long id,
    String email,
    String display_name,
    String role,
    boolean active,
    Instant created_at
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole().name(),
            user.isActive(),
            user.getCreatedAt()
        );
    }
}
