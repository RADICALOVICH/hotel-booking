package ru.hotelbooking.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedHandler;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

import ru.hotelbooking.support.security.BookingJwtClaims;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class GatewaySecurity {
    private static final String ROUTE_ACCESS = GatewaySecurity.class.getName() + ".routeAccess";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_USER = "ROLE_USER";
    private static final OAuth2Error INVALID_TOKEN =
            new OAuth2Error("invalid_token", "Token is not a user access token", null);

    @Bean
    ServerExchangeRejectedHandler gatewayRejectedRequest(GatewayErrors errors) {
        return (exchange, failure) -> errors.badRequest(exchange);
    }

    @Bean
    SecurityWebFilterChain gatewaySecurityChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder decoder,
            BookingAccessClient booking,
            GatewayErrors errors) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .addFilterBefore(
                        (exchange, chain) -> allowRoute(exchange, chain, errors),
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(
                        (exchange, chain) -> verifyAccess(exchange, chain, booking, errors),
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(
                        exchanges -> exchanges.anyExchange().access(GatewaySecurity::authorize))
                .exceptionHandling(
                        handlers ->
                                handlers.authenticationEntryPoint(
                                                (exchange, failure) ->
                                                        errors.unauthorized(exchange))
                                        .accessDeniedHandler(
                                                (exchange, failure) -> errors.forbidden(exchange)))
                .oauth2ResourceServer(
                        resource ->
                                resource.authenticationEntryPoint(
                                                (exchange, failure) ->
                                                        errors.unauthorized(exchange))
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtDecoder(decoder)
                                                                .jwtAuthenticationConverter(
                                                                        roles())))
                .build();
    }

    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(
            @Value("${gateway.jwt.public-key}") Resource pem,
            @Value("${gateway.jwt.issuer}") String issuer)
            throws IOException {
        NimbusReactiveJwtDecoder decoder;
        try (var input = pem.getInputStream()) {
            decoder =
                    NimbusReactiveJwtDecoder.withPublicKey(RsaKeyConverters.x509().convert(input))
                            .signatureAlgorithm(SignatureAlgorithm.RS256)
                            .build();
        }
        decoder.setJwtValidator(
                BookingJwtClaims.validate(
                        issuer, Clock.systemUTC(), GatewaySecurity::validateUser));
        return decoder;
    }

    private static OAuth2TokenValidatorResult validateUser(Jwt jwt) {
        try {
            String subject = jwt.getSubject();
            String role = jwt.getClaimAsString(BookingJwtClaims.ROLE_CLAIM);
            String version = jwt.getClaimAsString(BookingJwtClaims.VERSION_CLAIM);
            if (List.of(BookingJwtClaims.USER_AUDIENCE).equals(jwt.getAudience())
                    && subject != null
                    && subject.matches("[1-9][0-9]{0,18}")
                    && Long.parseLong(subject) > 0
                    && BookingJwtClaims.isUserRole(role)
                    && version != null
                    && UUID.fromString(version).toString().equalsIgnoreCase(version)) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException ignored) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }

    private static ReactiveJwtAuthenticationConverterAdapter roles() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(BookingJwtClaims.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    private static Mono<AuthorizationDecision> authorize(
            Mono<Authentication> authentication, AuthorizationContext context) {
        GatewayRoutes.Access access = context.getExchange().getRequiredAttribute(ROUTE_ACCESS);
        if (access == GatewayRoutes.Access.PUBLIC) {
            return Mono.just(new AuthorizationDecision(true));
        }
        Set<String> roles =
                access == GatewayRoutes.Access.ADMIN
                        ? Set.of(ROLE_ADMIN)
                        : Set.of(ROLE_ADMIN, ROLE_USER);
        return authentication
                .map(auth -> new AuthorizationDecision(hasRole(auth, roles)))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private static boolean hasRole(Authentication authentication, Set<String> roles) {
        return authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(roles::contains);
    }

    private static Mono<Void> allowRoute(
            ServerWebExchange exchange, WebFilterChain chain, GatewayErrors errors) {
        var access = GatewayRoutes.access(exchange);
        if (access.isEmpty()) {
            return errors.notFound(exchange);
        }
        exchange.getAttributes().put(ROUTE_ACCESS, access.get());
        return chain.filter(exchange);
    }

    private static Mono<Void> verifyAccess(
            ServerWebExchange exchange,
            WebFilterChain chain,
            BookingAccessClient booking,
            GatewayErrors errors) {
        if (exchange.getRequiredAttribute(ROUTE_ACCESS) == GatewayRoutes.Access.PUBLIC) {
            return chain.filter(exchange);
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .ofType(JwtAuthenticationToken.class)
                .flatMap(
                        token ->
                                booking.check(
                                        token.getToken().getTokenValue(),
                                        GatewayTraceFilter.traceId(exchange)))
                .defaultIfEmpty(BookingAccessClient.Result.CURRENT)
                .flatMap(
                        result ->
                                switch (result) {
                                    case CURRENT -> chain.filter(exchange);
                                    case REVOKED -> errors.unauthorized(exchange);
                                    case UNAVAILABLE -> errors.authUnavailable(exchange);
                                });
    }
}
