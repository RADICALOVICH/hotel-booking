package ru.hotelbooking.gateway;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;

import static ru.hotelbooking.gateway.GatewayRoutes.Access.ADMIN;
import static ru.hotelbooking.gateway.GatewayRoutes.Access.PUBLIC;
import static ru.hotelbooking.gateway.GatewayRoutes.Access.USER_OR_ADMIN;
import static ru.hotelbooking.gateway.GatewayRoutes.Target.BOOKING;
import static ru.hotelbooking.gateway.GatewayRoutes.Target.HOTEL;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Optional;

@Configuration(proxyBeanMethods = false)
class GatewayRoutes {
    private static final String BOOKING_PREFIX = "/api/bookings";
    private static final String ID = "/{id:[0-9]+}";
    private static final List<Endpoint> ENDPOINTS =
            List.of(
                    endpoint(POST, "/user/register", PUBLIC, BOOKING),
                    endpoint(POST, "/user/auth", PUBLIC, BOOKING),
                    endpoint(POST, "/user", ADMIN, BOOKING),
                    endpoint(GET, "/users", ADMIN, BOOKING),
                    endpoint(GET, "/user" + ID, ADMIN, BOOKING),
                    endpoint(PATCH, "/user" + ID, ADMIN, BOOKING),
                    endpoint(DELETE, "/user" + ID, ADMIN, BOOKING),
                    endpoint(POST, "/booking", USER_OR_ADMIN, BOOKING),
                    endpoint(GET, "/bookings", USER_OR_ADMIN, BOOKING),
                    endpoint(GET, "/booking" + ID, USER_OR_ADMIN, BOOKING),
                    endpoint(DELETE, "/booking" + ID, USER_OR_ADMIN, BOOKING),
                    endpoint(POST, "/booking" + ID + "/compensation", ADMIN, BOOKING),
                    endpoint(GET, "/rooms/recommend", USER_OR_ADMIN, BOOKING),
                    endpoint(GET, "/api/hotels", USER_OR_ADMIN, HOTEL),
                    endpoint(POST, "/api/hotels", ADMIN, HOTEL),
                    endpoint(GET, "/api/hotels" + ID, USER_OR_ADMIN, HOTEL),
                    endpoint(PATCH, "/api/hotels" + ID, ADMIN, HOTEL),
                    endpoint(DELETE, "/api/hotels" + ID, ADMIN, HOTEL),
                    endpoint(GET, "/api/hotels/statistics", ADMIN, HOTEL),
                    endpoint(GET, "/api/rooms", USER_OR_ADMIN, HOTEL),
                    endpoint(POST, "/api/rooms", ADMIN, HOTEL),
                    endpoint(GET, "/api/rooms" + ID, USER_OR_ADMIN, HOTEL),
                    endpoint(PATCH, "/api/rooms" + ID, ADMIN, HOTEL),
                    endpoint(DELETE, "/api/rooms" + ID, ADMIN, HOTEL),
                    endpoint(GET, "/api/rooms/recommend", USER_OR_ADMIN, HOTEL),
                    endpoint(GET, "/api/rooms/catalog", ADMIN, HOTEL),
                    endpoint(GET, "/api/rooms/statistics", ADMIN, HOTEL));

    @Bean
    RouteLocator businessRoutes(RouteLocatorBuilder routes) {
        var builder = routes.routes();
        for (Endpoint endpoint : ENDPOINTS) {
            builder.route(
                    endpoint.routeId(),
                    spec ->
                            spec.predicate(endpoint::matches)
                                    .filters(
                                            filters ->
                                                    endpoint.target() == BOOKING
                                                            ? filters.stripPrefix(2)
                                                                    .rewriteResponseHeader(
                                                                            HttpHeaders.LOCATION,
                                                                            "^(/(?:booking|user)/[0-9]+)$",
                                                                            BOOKING_PREFIX + "$1")
                                                            : filters)
                                    .uri(endpoint.target().uri));
        }
        return builder.build();
    }

    static Optional<Access> access(ServerWebExchange exchange) {
        if (exchange.getRequest().getMethod() == GET
                && exchange.getRequest().getPath().value().equals("/actuator/health")) {
            return Optional.of(PUBLIC);
        }
        return ENDPOINTS.stream()
                .filter(endpoint -> endpoint.matches(exchange))
                .findFirst()
                .map(Endpoint::access);
    }

    private static Endpoint endpoint(HttpMethod method, String path, Access access, Target target) {
        String external = target == BOOKING ? BOOKING_PREFIX + path : path;
        return new Endpoint(
                method, PathPatternParser.defaultInstance.parse(external), access, target);
    }

    enum Access {
        PUBLIC,
        USER_OR_ADMIN,
        ADMIN
    }

    enum Target {
        BOOKING("lb://booking-service"),
        HOTEL("lb://hotel-service");

        private final String uri;

        Target(String uri) {
            this.uri = uri;
        }
    }

    private record Endpoint(HttpMethod method, PathPattern path, Access access, Target target) {
        String routeId() {
            return method.name() + " " + path.getPatternString();
        }

        boolean matches(ServerWebExchange exchange) {
            return method.equals(exchange.getRequest().getMethod())
                    && path.matches(exchange.getRequest().getPath().pathWithinApplication());
        }
    }
}
