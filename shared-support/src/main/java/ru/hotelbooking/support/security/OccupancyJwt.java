package ru.hotelbooking.support.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** Separate, narrowly scoped credentials for Hotel's read-only occupancy callback. */
public final class OccupancyJwt {
    public static final String PATH = "/internal/bookings/occupancy";
    private static final String ISSUER = "http://hotel-service";
    private static final String AUDIENCE = "booking-occupancy";
    private static final String SUBJECT = "hotel-service";

    private OccupancyJwt() {}

    public static JwtEncoder encoder(String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key(secret)));
    }

    public static JwtDecoder decoder(String secret, Clock clock) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(key(secret))
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();
        decoder.setJwtValidator(
                BookingJwtClaims.validate(
                        ISSUER,
                        clock,
                        new DelegatingOAuth2TokenValidator<>(
                                new JwtClaimValidator<List<String>>(
                                        "aud", List.of(AUDIENCE)::equals),
                                new JwtClaimValidator<String>("sub", SUBJECT::equals))));
        return decoder;
    }

    public static String issue(JwtEncoder encoder, Clock clock) {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var claims =
                JwtClaimsSet.builder()
                        .issuer(ISSUER)
                        .subject(SUBJECT)
                        .audience(List.of(AUDIENCE))
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(60))
                        .build();
        return encoder.encode(
                        JwtEncoderParameters.from(
                                JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static SecretKey key(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32 || secret.isBlank()) {
            throw new IllegalStateException(
                    "Set OCCUPANCY_SHARED_SECRET to at least 32 random bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
