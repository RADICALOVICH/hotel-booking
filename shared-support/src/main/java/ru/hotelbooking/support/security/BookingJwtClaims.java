package ru.hotelbooking.support.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class BookingJwtClaims {
    public static final String USER_AUDIENCE = "hotel-booking-api";
    public static final String SERVICE_AUDIENCE = "hotel-internal";
    public static final String SERVICE_SUBJECT = "booking-service";
    public static final String ROLE_CLAIM = "role";
    public static final String VERSION_CLAIM = "access_version";

    private BookingJwtClaims() {}

    public static boolean isUserRole(String role) {
        return "USER".equals(role) || "ADMIN".equals(role);
    }

    public static OAuth2TokenValidator<Jwt> validate(
            String issuer, Clock clock, OAuth2TokenValidator<Jwt> servicePolicy) {
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        return new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtIssuerValidator(issuer),
                servicePolicy);
    }
}
