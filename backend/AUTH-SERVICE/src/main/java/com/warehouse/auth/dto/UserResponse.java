package com.warehouse.auth.dto;

import com.warehouse.auth.entity.User;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String role
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name()
        );
    }
}
