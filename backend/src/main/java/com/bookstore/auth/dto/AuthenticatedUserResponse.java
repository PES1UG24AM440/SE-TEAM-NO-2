package com.bookstore.auth.dto;

import com.bookstore.auth.UserRole;

public record AuthenticatedUserResponse(Long id, String name, String email, UserRole role) {}
