package ru.hotelbooking.hotel.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Replaces Booking and real time: a local key pair signs test tokens, a stub answers revocation
 * checks, and the business clock is fixed at {@link #TODAY} in Moscow.
 */
@TestConfiguration(proxyBeanMethods = false)
public class HotelTestConfig {
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    @Bean
    @Primary
    Clock fixedHotelClock() {
        ZoneId moscow = ZoneId.of("Europe/Moscow");
        return Clock.fixed(TODAY.atTime(12, 0).atZone(moscow).toInstant(), moscow);
    }

    @Bean
    KeyPair testBookingKeys() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Bean
    RSAPublicKey bookingJwtPublicKey(KeyPair testBookingKeys) {
        return (RSAPublicKey) testBookingKeys.getPublic();
    }

    @Bean
    TestTokens testTokens(KeyPair testBookingKeys, @Value("${hotel.jwt.issuer}") String issuer) {
        return new TestTokens(testBookingKeys, issuer);
    }

    @Bean
    @Primary
    StubAccessVerifier stubAccessVerifier() {
        return new StubAccessVerifier();
    }
}
