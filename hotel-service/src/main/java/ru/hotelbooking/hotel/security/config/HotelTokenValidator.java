package ru.hotelbooking.hotel.security.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import ru.hotelbooking.support.security.BookingJwtClaims;

import java.util.List;

/** Accepts either a user token or a Booking service token, never a mix of both. */
class HotelTokenValidator implements OAuth2TokenValidator<Jwt> {
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audience = jwt.getAudience() == null ? List.of() : jwt.getAudience();
        boolean user = audience.contains(BookingJwtClaims.USER_AUDIENCE);
        boolean service = audience.contains(BookingJwtClaims.SERVICE_AUDIENCE);
        if (user == service) {
            return invalid("Token audience is not accepted by Hotel");
        }
        return service ? validateService(jwt) : validateUser(jwt);
    }

    private static OAuth2TokenValidatorResult validateService(Jwt jwt) {
        return BookingJwtClaims.SERVICE_SUBJECT.equals(jwt.getSubject())
                ? OAuth2TokenValidatorResult.success()
                : invalid("Service token subject is not trusted");
    }

    private static OAuth2TokenValidatorResult validateUser(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || !subject.matches("[1-9][0-9]{0,18}")) {
            return invalid("User token subject must be a user id");
        }
        if (!BookingJwtClaims.isUserRole(jwt.getClaimAsString(BookingJwtClaims.ROLE_CLAIM))
                || jwt.getClaimAsString(BookingJwtClaims.VERSION_CLAIM) == null) {
            return invalid("User token lacks role or access version");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult invalid(String description) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", description, null));
    }
}
