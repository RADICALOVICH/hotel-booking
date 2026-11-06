package ru.hotelbooking.booking.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;

@Configuration(proxyBeanMethods = false)
class PasswordConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new Pbkdf2PasswordEncoder(
                "", 16, 600_000, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    }
}
