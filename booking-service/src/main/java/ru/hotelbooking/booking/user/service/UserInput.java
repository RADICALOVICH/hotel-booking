package ru.hotelbooking.booking.user.service;

import ru.hotelbooking.booking.user.entity.UserRole;
import ru.hotelbooking.booking.user.exception.UserInputException;

import java.util.Locale;

final class UserInput {
    private UserInput() {}

    static String username(String username) {
        if (username == null || username.isBlank()) {
            throw new UserInputException("username", "Username is required");
        }
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64) {
            throw new UserInputException("username", "Username must be at most 64 characters");
        }
        return normalized;
    }

    static void password(String password) {
        if (password == null || password.isBlank()) {
            throw new UserInputException("password", "Password is required");
        }
        int length = password.codePointCount(0, password.length());
        if (length < 12 || length > 128) {
            throw new UserInputException(
                    "password", "Password must be between 12 and 128 characters");
        }
    }

    static UserRole role(String role) {
        if (role == null || role.isBlank()) {
            throw new UserInputException("role", "Role is required");
        }
        return switch (role.strip().toUpperCase(Locale.ROOT)) {
            case "USER" -> UserRole.USER;
            case "ADMIN" -> UserRole.ADMIN;
            default -> throw new UserInputException("role", "Role must be USER or ADMIN");
        };
    }
}
