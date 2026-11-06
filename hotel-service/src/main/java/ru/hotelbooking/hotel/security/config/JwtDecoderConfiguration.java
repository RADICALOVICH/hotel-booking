package ru.hotelbooking.hotel.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import ru.hotelbooking.support.security.BookingJwtClaims;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class JwtDecoderConfiguration {
    @Bean
    @Profile("!test")
    RSAPublicKey bookingJwtPublicKey(@Value("${hotel.jwt.public-key}") Resource pem)
            throws IOException {
        try (InputStream input = pem.getInputStream()) {
            RSAPublicKey key = RsaKeyConverters.x509().convert(input);
            if (key.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("Booking JWT key must be at least 2048 bits");
            }
            return key;
        }
    }

    @Bean
    JwtDecoder jwtDecoder(RSAPublicKey bookingKey, @Value("${hotel.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey(bookingKey)
                        .signatureAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        decoder.setJwtValidator(
                BookingJwtClaims.validate(issuer, Clock.systemUTC(), new HotelTokenValidator()));
        return decoder;
    }
}
