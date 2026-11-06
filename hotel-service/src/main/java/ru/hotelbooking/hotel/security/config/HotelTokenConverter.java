package ru.hotelbooking.hotel.security.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import ru.hotelbooking.support.security.BookingJwtClaims;

/** Maps a validated token to ROLE_USER, ROLE_ADMIN or ROLE_SERVICE. */
class HotelTokenConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String role =
                HotelTokens.isServiceToken(jwt)
                        ? "SERVICE"
                        : jwt.getClaimAsString(BookingJwtClaims.ROLE_CLAIM);
        return new JwtAuthenticationToken(
                jwt, AuthorityUtils.createAuthorityList("ROLE_" + role), jwt.getSubject());
    }
}
