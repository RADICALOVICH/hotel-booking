package ru.hotelbooking.eureka;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
class RegistrySecurity {
    @Bean
    SecurityFilterChain registrySecurityChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers(HttpMethod.GET, "/actuator/health")
                                        .permitAll()
                                        .anyRequest()
                                        .hasRole("EUREKA"))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**"))
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(withDefaults())
                .build();
    }

    @Bean
    PasswordEncoder registryPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService registryAccount(
            @Value("${EUREKA_USERNAME:}") String username,
            @Value("${EUREKA_PASSWORD:}") String password,
            PasswordEncoder passwords) {
        if (username.isBlank() || username.contains(":") || password.isBlank()) {
            throw new IllegalStateException(
                    "Set non-blank EUREKA_USERNAME (without a colon) and EUREKA_PASSWORD");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("EUREKA_PASSWORD must fit in 72 UTF-8 bytes");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(passwords.encode(password))
                        .roles("EUREKA")
                        .build());
    }
}
