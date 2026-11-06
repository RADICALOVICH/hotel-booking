package ru.hotelbooking.booking.security.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import ru.hotelbooking.booking.security.service.JwtTokenService;
import ru.hotelbooking.booking.user.service.UserService;

import java.io.IOException;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
class JwtConfiguration {
    private static final OAuth2Error INVALID_ACCESS =
            new OAuth2Error("invalid_token", "User access is no longer valid", null);

    @Bean
    Clock jwtClock() {
        return Clock.systemUTC();
    }

    @Bean
    @Profile("!test")
    KeyPair bookingJwtKeys(
            @Value("${booking.jwt.private-key}") Resource privatePem,
            @Value("${booking.jwt.public-key}") Resource publicPem)
            throws IOException {
        try (var privateInput = privatePem.getInputStream();
                var publicInput = publicPem.getInputStream()) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateInput);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicInput);
            if (!publicKey.getModulus().equals(privateKey.getModulus())
                    || publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalStateException(
                        "Booking JWT needs a matching RSA key pair of at least 2048 bits");
            }
            return new KeyPair(publicKey, privateKey);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair keys) {
        RSAKey signingKey =
                new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                        .privateKey(keys.getPrivate())
                        .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(
            KeyPair keys,
            Clock clock,
            UserService users,
            @Value("${booking.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic())
                        .signatureAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        var claims =
                new DelegatingOAuth2TokenValidator<Jwt>(
                        timestamps,
                        new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                        new JwtIssuerValidator(issuer),
                        new JwtClaimValidator<List<String>>(
                                JwtClaimNames.AUD,
                                audiences ->
                                        audiences != null
                                                && audiences.contains(JwtTokenService.AUDIENCE)));
        decoder.setJwtValidator(
                jwt -> {
                    var result = claims.validate(jwt);
                    return result.hasErrors() ? result : validateAccess(jwt, users);
                });
        return decoder;
    }

    private static OAuth2TokenValidatorResult validateAccess(Jwt jwt, UserService users) {
        String subject = jwt.getSubject();
        String version = jwt.getClaimAsString(JwtTokenService.VERSION_CLAIM);
        String role = jwt.getClaimAsString(JwtTokenService.ROLE_CLAIM);
        if (subject == null || version == null || role == null) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACCESS);
        }
        try {
            if (users.hasCurrentAccess(Long.parseLong(subject), UUID.fromString(version), role)) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException ignored) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACCESS);
        }
        return OAuth2TokenValidatorResult.failure(INVALID_ACCESS);
    }
}
