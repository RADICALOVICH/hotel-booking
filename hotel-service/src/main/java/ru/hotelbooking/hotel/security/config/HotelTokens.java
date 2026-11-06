package ru.hotelbooking.hotel.security.config;

import org.springframework.security.oauth2.jwt.Jwt;

import ru.hotelbooking.support.security.BookingJwtClaims;

/** Distinguishes service tokens for Hotel's request filters. */
public final class HotelTokens {
    private HotelTokens() {}

    public static boolean isServiceToken(Jwt jwt) {
        return jwt.getAudience() != null
                && jwt.getAudience().contains(BookingJwtClaims.SERVICE_AUDIENCE);
    }
}
