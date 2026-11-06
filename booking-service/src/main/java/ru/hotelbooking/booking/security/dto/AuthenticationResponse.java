package ru.hotelbooking.booking.security.dto;

import ru.hotelbooking.booking.user.entity.UserRole;

public record AuthenticationResponse(
        long id,
        String username,
        UserRole role,
        String accessToken,
        String tokenType,
        long expiresIn) {
    @Override
    public String toString() {
        return "AuthenticationResponse[redacted]";
    }
}
