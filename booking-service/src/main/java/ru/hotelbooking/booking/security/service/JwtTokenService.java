package ru.hotelbooking.booking.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import ru.hotelbooking.booking.security.dto.AuthenticationResponse;
import ru.hotelbooking.booking.user.dto.UserResponse;
import ru.hotelbooking.booking.user.dto.UserSession;
import ru.hotelbooking.support.security.BookingJwtClaims;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class JwtTokenService {
    private static final long LIFETIME_SECONDS = 3_600;
    private static final long SERVICE_LIFETIME_SECONDS = 300;

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private Jwt cachedServiceToken;

    JwtTokenService(
            JwtEncoder encoder, Clock clock, @Value("${booking.jwt.issuer}") String issuer) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
    }

    public AuthenticationResponse issue(UserSession session) {
        UserResponse user = session.user();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .subject(Long.toString(user.id()))
                        .audience(List.of(BookingJwtClaims.USER_AUDIENCE))
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(LIFETIME_SECONDS))
                        .claim(BookingJwtClaims.ROLE_CLAIM, user.role().name())
                        .claim(BookingJwtClaims.VERSION_CLAIM, session.accessVersion().toString())
                        .build();
        String token = sign(claims).getTokenValue();
        return new AuthenticationResponse(
                user.id(), user.username(), user.role(), token, "Bearer", LIFETIME_SECONDS);
    }

    public synchronized String serviceToken() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        if (cachedServiceToken == null || !now.isBefore(cachedServiceToken.getExpiresAt())) {
            cachedServiceToken =
                    sign(
                            JwtClaimsSet.builder()
                                    .issuer(issuer)
                                    .subject(BookingJwtClaims.SERVICE_SUBJECT)
                                    .audience(List.of(BookingJwtClaims.SERVICE_AUDIENCE))
                                    .issuedAt(now)
                                    .expiresAt(now.plusSeconds(SERVICE_LIFETIME_SECONDS))
                                    .build());
        }
        return cachedServiceToken.getTokenValue();
    }

    private Jwt sign(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims));
    }
}
