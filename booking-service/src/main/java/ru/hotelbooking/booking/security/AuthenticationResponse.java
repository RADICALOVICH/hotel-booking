package ru.hotelbooking.booking.security;

import ru.hotelbooking.booking.user.UserRole;

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
