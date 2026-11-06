package ru.hotelbooking.booking.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import ru.hotelbooking.booking.security.dto.AuthenticationResponse;
import ru.hotelbooking.booking.user.dto.UserResponse;
import ru.hotelbooking.booking.user.dto.UserSession;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class JwtTokenService {
    public static final String AUDIENCE = "hotel-booking-api";
    public static final String ROLE_CLAIM = "role";
    public static final String VERSION_CLAIM = "access_version";
    private static final long LIFETIME_SECONDS = 3_600;

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;

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
                        .audience(List.of(AUDIENCE))
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(LIFETIME_SECONDS))
                        .claim(ROLE_CLAIM, user.role().name())
                        .claim(VERSION_CLAIM, session.accessVersion().toString())
                        .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthenticationResponse(
                user.id(), user.username(), user.role(), token, "Bearer", LIFETIME_SECONDS);
    }
}
