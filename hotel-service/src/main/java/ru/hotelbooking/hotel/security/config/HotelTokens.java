package ru.hotelbooking.hotel.security.config;

import org.springframework.security.oauth2.jwt.Jwt;

/** Claims shared with Booking, which issues both user and service tokens. */
public final class HotelTokens {
    public static final String USER_AUDIENCE = "hotel-booking-api";
    public static final String SERVICE_AUDIENCE = "hotel-internal";
    public static final String SERVICE_SUBJECT = "booking-service";
    public static final String ROLE_CLAIM = "role";
    public static final String VERSION_CLAIM = "access_version";

    private HotelTokens() {}

    public static boolean isServiceToken(Jwt jwt) {
        return jwt.getAudience() != null && jwt.getAudience().contains(SERVICE_AUDIENCE);
    }
}
