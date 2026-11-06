package ru.hotelbooking.hotel.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import ru.hotelbooking.hotel.security.client.AccessVerifier;
import ru.hotelbooking.hotel.security.web.AccessVersionFilter;
import ru.hotelbooking.hotel.security.web.SecurityErrorWriter;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class HotelSecurityConfiguration {
    private static final String[] INTERNAL_ROOM_ACTIONS = {
        "/api/rooms/{id}/confirm-availability", "/api/rooms/{id}/release"
    };

    @Bean
    SecurityFilterChain hotelSecurity(
            HttpSecurity http, SecurityErrorWriter errors, AccessVerifier verifier)
            throws Exception {
        AuthenticationEntryPoint unauthorized =
                (request, response, exception) ->
                        errors.write(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "A valid access token is required");
        AccessDeniedHandler forbidden =
                (request, response, exception) ->
                        errors.write(
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
                                routes.requestMatchers(HttpMethod.GET, "/actuator/health")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, INTERNAL_ROOM_ACTIONS)
                                        .hasRole("SERVICE")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/rooms/catalog",
                                                "/api/rooms/statistics")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/hotels",
                                                "/api/hotels/{id}",
                                                "/api/rooms",
                                                "/api/rooms/recommend",
                                                "/api/rooms/{id}")
                                        .hasAnyRole("USER", "ADMIN")
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/hotels", "/api/rooms")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.PATCH,
                                                "/api/hotels/{id}",
                                                "/api/rooms/{id}")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.DELETE,
                                                "/api/hotels/{id}",
                                                "/api/rooms/{id}")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        failures ->
                                failures.authenticationEntryPoint(unauthorized)
                                        .accessDeniedHandler(forbidden))
                .oauth2ResourceServer(
                        resource ->
                                resource.authenticationEntryPoint(unauthorized)
                                        .accessDeniedHandler(forbidden)
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                new HotelTokenConverter())))
                .addFilterAfter(
                        new AccessVersionFilter(verifier, errors),
                        BearerTokenAuthenticationFilter.class)
                .build();
    }
}
