package ru.hotelbooking.booking.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import ru.hotelbooking.booking.common.error.ErrorDTO;
import ru.hotelbooking.booking.security.service.JwtTokenService;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
class BookingSecurityConfiguration {
    @Bean
    SecurityFilterChain bookingSecurity(HttpSecurity http, ObjectMapper json) throws Exception {
        AuthenticationEntryPoint unauthorized =
                (request, response, exception) -> {
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                    writeError(
                            json,
                            request,
                            response,
                            HttpStatus.UNAUTHORIZED,
                            "UNAUTHORIZED",
                            "A valid access token is required");
                };
        AccessDeniedHandler forbidden =
                (request, response, exception) ->
                        writeError(
                                json,
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "ACCESS_DENIED",
                                "Access denied");

        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        routes ->
                                routes.requestMatchers(
                                                HttpMethod.POST, "/user/register", "/user/auth")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/internal/auth/validate")
                                        .hasAnyRole("USER", "ADMIN")
                                        .requestMatchers("/user", "/users", "/user/{id}")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(unauthorized)
                                        .accessDeniedHandler(forbidden))
                .oauth2ResourceServer(
                        resource ->
                                resource.authenticationEntryPoint(unauthorized)
                                        .accessDeniedHandler(forbidden)
                                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roles())))
                .build();
    }

    private static JwtAuthenticationConverter roles() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtTokenService.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static void writeError(
            ObjectMapper json,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        json.writeValue(
                response.getOutputStream(),
                new ErrorDTO(code, message, Instant.now(), request.getRequestURI(), Map.of()));
    }
}
