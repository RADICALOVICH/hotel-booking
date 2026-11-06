package ru.hotelbooking.hotel.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

/** Replaces Booking: a local key pair signs test tokens, a stub answers revocation checks. */
@TestConfiguration(proxyBeanMethods = false)
public class HotelSecurityTestConfig {
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
