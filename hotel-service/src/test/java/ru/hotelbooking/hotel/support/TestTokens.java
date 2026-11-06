package ru.hotelbooking.hotel.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import ru.hotelbooking.hotel.security.config.HotelTokens;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Signs tokens the way Booking does, so tests pass real JWTs through Spring Security. */
public class TestTokens {
    private final JwtEncoder encoder;
    private final String issuer;

    public TestTokens(KeyPair keys, String issuer) {
        this.encoder = encoderFor(keys);
        this.issuer = issuer;
    }

    public static JwtEncoder encoderFor(KeyPair keys) {
        RSAKey key =
                new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                        .privateKey(keys.getPrivate())
                        .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    public String user() {
        return user(41, "USER");
    }

    public String admin() {
        return user(42, "ADMIN");
    }

    public String user(long id, String role) {
        return sign(
                claims ->
                        claims.subject(Long.toString(id))
                                .audience(List.of(HotelTokens.USER_AUDIENCE))
                                .claim(HotelTokens.ROLE_CLAIM, role)
                                .claim(HotelTokens.VERSION_CLAIM, UUID.randomUUID().toString()));
    }

    public String service() {
        return sign(
                claims ->
                        claims.subject(HotelTokens.SERVICE_SUBJECT)
                                .audience(List.of(HotelTokens.SERVICE_AUDIENCE)));
    }

    /** Starts from a valid USER token; the customizer breaks exactly one aspect. */
    public String userWith(Consumer<JwtClaimsSet.Builder> customizer) {
        return sign(
                claims -> {
                    claims.subject("41")
                            .audience(List.of(HotelTokens.USER_AUDIENCE))
                            .claim(HotelTokens.ROLE_CLAIM, "USER")
                            .claim(HotelTokens.VERSION_CLAIM, UUID.randomUUID().toString());
                    customizer.accept(claims);
                });
    }

    public String signWith(JwtEncoder otherEncoder) {
        return encode(
                otherEncoder,
                baseClaims()
                        .subject("41")
                        .audience(List.of(HotelTokens.USER_AUDIENCE))
                        .claim(HotelTokens.ROLE_CLAIM, "USER")
                        .claim(HotelTokens.VERSION_CLAIM, UUID.randomUUID().toString()));
    }

    private String sign(Consumer<JwtClaimsSet.Builder> claims) {
        JwtClaimsSet.Builder builder = baseClaims();
        claims.accept(builder);
        return encode(encoder, builder);
    }

    private JwtClaimsSet.Builder baseClaims() {
        Instant now = Instant.now();
        return JwtClaimsSet.builder().issuer(issuer).issuedAt(now).expiresAt(now.plusSeconds(3600));
    }

    private static String encode(JwtEncoder encoder, JwtClaimsSet.Builder claims) {
        return encoder.encode(
                        JwtEncoderParameters.from(
                                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }
}
