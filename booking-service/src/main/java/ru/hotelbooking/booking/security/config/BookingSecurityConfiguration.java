package ru.hotelbooking.booking.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import ru.hotelbooking.support.security.BookingJwtClaims;
import ru.hotelbooking.support.web.ServletSecurityErrors;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class BookingSecurityConfiguration {
    @Bean
    SecurityFilterChain bookingSecurity(HttpSecurity http, ObjectMapper json) throws Exception {
        AuthenticationEntryPoint unauthorized =
                (request, response, exception) ->
                        ServletSecurityErrors.write(
                                json,
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "A valid access token is required");
        AccessDeniedHandler forbidden =
                (request, response, exception) ->
                        ServletSecurityErrors.write(
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
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/v3/api-docs",
                                                "/v3/api-docs/**",
                                                "/v3/api-docs.yaml",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/internal/auth/validate")
                                        .hasAnyRole("USER", "ADMIN")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/bookings",
                                                "/booking/{id}",
                                                "/rooms/recommend")
                                        .hasAnyRole("USER", "ADMIN")
                                        .requestMatchers(
                                                HttpMethod.POST, "/booking/{id}/compensation")
                                        .hasRole("ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/booking")
                                        .hasAnyRole("USER", "ADMIN")
                                        .requestMatchers(HttpMethod.DELETE, "/booking/{id}")
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
        authorities.setAuthoritiesClaimName(BookingJwtClaims.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
