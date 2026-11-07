package ru.hotelbooking.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayContractTest {
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String TRACE = "0123456789abcdefFEDCBA9876543210";
    private static final String TRACE_PATTERN = "[a-f0-9]{32}";
    private static final String BOOKING_PREFIX = "/api/bookings";
    private static final String AUTH_CHECK = "/internal/auth/validate";
    private static final String BOOKING_ISSUER = "http://booking-service";
    private static final String API_AUDIENCE = "hotel-booking-api";
    private static final String REQUEST_BODY = "{\"sample\":\"payload\"}";
    private static final Stub BOOKING = new Stub();
    private static final Stub HOTEL = new Stub();
    private static final KeyPair KEY = keyPair();
    private static final Path PUBLIC_KEY = pem(KEY);
    private static final NimbusJwtEncoder ENCODER =
            new NimbusJwtEncoder(
                    new ImmutableJWKSet<>(
                            new JWKSet(
                                    new RSAKey.Builder((RSAPublicKey) KEY.getPublic())
                                            .privateKey(KEY.getPrivate())
                                            .build())));

    @Autowired private WebTestClient client;
    @LocalServerPort private int port;

    @DynamicPropertySource
    static void localDiscovery(DynamicPropertyRegistry props) {
        props.add("eureka.client.enabled", () -> false);
        props.add("gateway.jwt.public-key", () -> PUBLIC_KEY.toUri().toString());
        props.add(
                "spring.cloud.discovery.client.simple.instances.booking-service[0].uri",
                BOOKING::url);
        props.add(
                "spring.cloud.discovery.client.simple.instances.hotel-service[0].uri", HOTEL::url);
    }

    @BeforeEach
    void resetStubs() {
        BOOKING.reset();
        HOTEL.reset();
    }

    @AfterAll
    static void stopStubs() throws IOException {
        BOOKING.close();
        HOTEL.close();
        Files.deleteIfExists(PUBLIC_KEY);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publicRoutes")
    void forwardsEachContractRouteOnlyToItsService(Route route) {
        String bearer = route.role() == null ? null : token(route.role());
        var request =
                client.method(route.method()).uri(route.externalPath()).header(TRACE_HEADER, TRACE);
        if (bearer != null) {
            request.headers(headers -> headers.setBearerAuth(bearer));
        }
        WebTestClient.RequestHeadersSpec<?> prepared =
                route.method().equals(POST) || route.method().equals(PATCH)
                        ? request.contentType(APPLICATION_JSON).bodyValue(REQUEST_BODY)
                        : request;
        prepared.exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(TRACE_HEADER, TRACE)
                .expectBody()
                .json("{\"forwarded\":true}");

        List<Call> calls = route.service().businessCalls();
        assertThat(calls).hasSize(1);
        Call forwarded = calls.getFirst();
        assertThat(forwarded.method()).isEqualTo(route.method().name());
        assertThat(forwarded.path()).isEqualTo(route.downstreamPath());
        assertThat(forwarded.query()).isEqualTo(route.query());
        assertThat(forwarded.traceId()).isEqualTo(TRACE);
        assertThat(forwarded.body())
                .isEqualTo(
                        route.method().equals(POST) || route.method().equals(PATCH)
                                ? REQUEST_BODY
                                : "");
        assertThat(authorizationMatches(forwarded, bearer)).isTrue();
        assertThat(route.service() == BOOKING ? HOTEL.businessCalls() : BOOKING.businessCalls())
                .isEmpty();
        if (bearer == null) {
            assertThat(BOOKING.authCalls()).isEmpty();
        } else {
            assertThat(BOOKING.authCalls()).hasSize(1);
            Call check = BOOKING.authCalls().getFirst();
            assertThat(check.method()).isEqualTo("GET");
            assertThat(check.traceId()).isEqualTo(TRACE);
            assertThat(authorizationMatches(check, bearer)).isTrue();
        }
    }

    static Stream<Route> publicRoutes() {
        return Stream.of(
                booking(POST, "/user/register", null),
                booking(POST, "/user/auth", null),
                booking(POST, "/user", "ADMIN"),
                booking(GET, "/users?page=1&size=3", "ADMIN"),
                booking(GET, "/user/17", "ADMIN"),
                booking(PATCH, "/user/17", "ADMIN"),
                booking(DELETE, "/user/17", "ADMIN"),
                booking(POST, "/booking", "USER"),
                booking(GET, "/bookings?page=1&size=3", "USER"),
                booking(GET, "/booking/17", "USER"),
                booking(DELETE, "/booking/17", "USER"),
                booking(POST, "/booking/17/compensation", "ADMIN"),
                booking(GET, "/rooms/recommend?hotelId=2&page=1&size=3", "USER"),
                hotel(GET, "/api/hotels?page=1&size=3", "USER"),
                hotel(GET, "/api/hotels/17", "USER"),
                hotel(POST, "/api/hotels", "ADMIN"),
                hotel(PATCH, "/api/hotels/17", "ADMIN"),
                hotel(DELETE, "/api/hotels/17", "ADMIN"),
                hotel(GET, "/api/hotels/statistics", "ADMIN"),
                hotel(GET, "/api/rooms?startDate=2026-12-03&endDate=2026-12-05&hotelId=2", "USER"),
                hotel(GET, "/api/rooms/17", "USER"),
                hotel(GET, "/api/rooms/recommend?hotelId=2&page=1&size=3", "USER"),
                hotel(GET, "/api/rooms/catalog", "ADMIN"),
                hotel(GET, "/api/rooms/statistics", "ADMIN"),
                hotel(POST, "/api/rooms", "ADMIN"),
                hotel(PATCH, "/api/rooms/17", "ADMIN"),
                hotel(DELETE, "/api/rooms/17", "ADMIN"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/rooms/17/confirm-availability",
                "/api/rooms/17/release",
                "/api/bookings/internal/auth/validate",
                "/internal/auth/validate",
                "/api/bookings/internal/bookings/occupancy",
                "/internal/bookings/occupancy",
                "/hotel-service/api/rooms/17",
                "/booking-service/booking/17"
            })
    void neverPublishesInternalOrDiscoveryDerivedRoutes(String path) {
        HttpMethod method = path.contains("/17/") ? POST : GET;
        client.method(method)
                .uri(path)
                .headers(headers -> headers.setBearerAuth(token("ADMIN")))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("NOT_FOUND")
                .jsonPath("$.path")
                .isEqualTo(path);
        assertThat(BOOKING.calls).isEmpty();
        assertThat(HOTEL.calls).isEmpty();
    }

    @Test
    void rejectsEncodedInternalPathWithTheSameTraceAndErrorEnvelope() {
        URI internal = URI.create("http://127.0.0.1:" + port + "/api/rooms/17%2Frelease");
        client.post()
                .uri(internal)
                .headers(headers -> headers.setBearerAuth(token("ADMIN")))
                .header(TRACE_HEADER, TRACE)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .valueEquals(TRACE_HEADER, TRACE)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("BAD_REQUEST")
                .jsonPath("$.traceId")
                .isEqualTo(TRACE);
        assertThat(BOOKING.calls).isEmpty();
        assertThat(HOTEL.calls).isEmpty();
    }

    @Test
    void rejectsUnsupportedMethodAndPreventsAUserFromUsingAdministrativeRoutes() {
        String bearer = token("USER");
        client.put()
                .uri("/api/hotels/17")
                .headers(headers -> headers.setBearerAuth(bearer))
                .exchange()
                .expectStatus()
                .isNotFound();
        client.get()
                .uri("/api/rooms/catalog")
                .headers(headers -> headers.setBearerAuth(bearer))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("ACCESS_DENIED");
        client.post()
                .uri("/api/bookings/booking/17/compensation")
                .headers(headers -> headers.setBearerAuth(bearer))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("ACCESS_DENIED");
        assertNoBusinessCalls();
    }

    @ParameterizedTest
    @EnumSource(InvalidToken.class)
    void rejectsMissingAndInvalidJwtBeforeConsultingBooking(InvalidToken invalid) {
        var request = client.get().uri("/api/rooms/17").header(TRACE_HEADER, TRACE);
        if (invalid != InvalidToken.MISSING) {
            request.headers(headers -> headers.setBearerAuth(invalid.value()));
        }
        request.exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .valueMatches(WWW_AUTHENTICATE, "Bearer.*")
                .expectHeader()
                .valueEquals(TRACE_HEADER, TRACE)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHORIZED")
                .jsonPath("$.traceId")
                .isEqualTo(TRACE)
                .jsonPath("$.path")
                .isEqualTo("/api/rooms/17");
        assertThat(BOOKING.calls).isEmpty();
        assertThat(HOTEL.calls).isEmpty();
    }

    @Test
    void checksRevocationOnEveryRequestAndFailsClosedWhenBookingIsUnavailable() {
        String bearer = token("USER");
        client.get()
                .uri("/api/rooms/17")
                .headers(headers -> headers.setBearerAuth(bearer))
                .exchange()
                .expectStatus()
                .isOk();
        BOOKING.authStatus = 401;
        client.get()
                .uri("/api/rooms/17")
                .headers(headers -> headers.setBearerAuth(bearer))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHORIZED");
        BOOKING.authStatus = 500;
        client.get()
                .uri("/api/rooms/17")
                .headers(headers -> headers.setBearerAuth(bearer))
                .header(TRACE_HEADER, TRACE)
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectHeader()
                .valueEquals(TRACE_HEADER, TRACE)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("AUTH_SERVICE_UNAVAILABLE")
                .jsonPath("$.traceId")
                .isEqualTo(TRACE);
        assertThat(BOOKING.authCalls()).hasSize(3);
        assertThat(HOTEL.businessCalls()).hasSize(1);
        assertThat(BOOKING.businessCalls()).isEmpty();
    }

    @Test
    void generatesTraceForMissingInvalidOrRepeatedHeadersAndPreservesDownstreamError() {
        HOTEL.reply = new Reply(409, "{\"code\":\"ROOM_UNAVAILABLE\",\"bookingId\":17}", Map.of());
        String bearer = token("USER");
        List<List<String>> incoming = List.of(List.of(), List.of("invalid"), List.of(TRACE, TRACE));
        for (List<String> values : incoming) {
            client.get()
                    .uri("/api/rooms/17")
                    .headers(
                            headers -> {
                                headers.setBearerAuth(bearer);
                                values.forEach(value -> headers.add(TRACE_HEADER, value));
                            })
                    .exchange()
                    .expectStatus()
                    .isEqualTo(409)
                    .expectHeader()
                    .valueMatches(TRACE_HEADER, TRACE_PATTERN)
                    .expectHeader()
                    .value(
                            TRACE_HEADER,
                            trace -> {
                                assertThat(HOTEL.businessCalls().getLast().traceId())
                                        .isEqualTo(trace);
                                assertThat(BOOKING.authCalls().getLast().traceId())
                                        .isEqualTo(trace);
                            })
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo("ROOM_UNAVAILABLE")
                    .jsonPath("$.bookingId")
                    .isEqualTo(17);
        }
        assertThat(HOTEL.businessCalls()).extracting(Call::traceId).doesNotHaveDuplicates();
    }

    @Test
    void rewritesBookingLocationAndLeavesHotelLocationIntact() {
        BOOKING.reply = new Reply(202, "{}", Map.of(LOCATION, "/booking/17"));
        client.post()
                .uri("/api/bookings/booking")
                .headers(headers -> headers.setBearerAuth(token("USER")))
                .exchange()
                .expectStatus()
                .isAccepted()
                .expectHeader()
                .valueEquals(LOCATION, "/api/bookings/booking/17");
        HOTEL.reply = new Reply(201, "{}", Map.of(LOCATION, "/api/hotels/17"));
        client.post()
                .uri("/api/hotels")
                .headers(headers -> headers.setBearerAuth(token("ADMIN")))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueEquals(LOCATION, "/api/hotels/17");
    }

    private static boolean authorizationMatches(Call call, String token) {
        return token == null
                ? call.authorization() == null
                : ("Bearer " + token).equals(call.authorization());
    }

    private static void assertNoBusinessCalls() {
        assertThat(BOOKING.businessCalls()).isEmpty();
        assertThat(HOTEL.businessCalls()).isEmpty();
    }

    private static Route booking(HttpMethod method, String path, String role) {
        return new Route(BOOKING, method, BOOKING_PREFIX + path, path, role);
    }

    private static Route hotel(HttpMethod method, String path, String role) {
        return new Route(HOTEL, method, path, path, role);
    }

    private record Route(
            Stub service, HttpMethod method, String externalPath, String target, String role) {
        String downstreamPath() {
            return target.split("\\?", 2)[0];
        }

        String query() {
            int separator = target.indexOf('?');
            return separator < 0 ? null : target.substring(separator + 1);
        }

        @Override
        public String toString() {
            return method + " " + externalPath;
        }
    }

    private static String token(String role) {
        return sign(
                role,
                BOOKING_ISSUER,
                List.of(API_AUDIENCE),
                "17",
                UUID.randomUUID().toString(),
                false);
    }

    private static String sign(
            String role,
            String issuer,
            List<String> audience,
            String subject,
            String accessVersion,
            boolean expired) {
        Instant now = Instant.now();
        var claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .subject(subject)
                        .audience(audience)
                        .issuedAt(now.minusSeconds(600))
                        .expiresAt(expired ? now.minusSeconds(120) : now.plusSeconds(600));
        if (role != null) {
            claims.claim("role", role);
        }
        if (accessVersion != null) {
            claims.claim("access_version", accessVersion);
        }
        return ENCODER.encode(
                        JwtEncoderParameters.from(
                                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }

    private enum InvalidToken {
        MISSING,
        SIGNATURE,
        EXPIRED,
        AUDIENCE,
        SERVICE,
        MIXED_AUDIENCE,
        ISSUER,
        SUBJECT,
        ROLE,
        VERSION;

        String value() {
            String version = UUID.randomUUID().toString();
            return switch (this) {
                case MISSING -> throw new IllegalStateException();
                case SIGNATURE -> {
                    String valid = token("USER");
                    int start = valid.lastIndexOf('.') + 1;
                    yield valid.substring(0, start)
                            + (valid.charAt(start) == 'A' ? 'B' : 'A')
                            + valid.substring(start + 1);
                }
                case EXPIRED ->
                        sign("USER", BOOKING_ISSUER, List.of(API_AUDIENCE), "17", version, true);
                case AUDIENCE ->
                        sign("USER", BOOKING_ISSUER, List.of("other"), "17", version, false);
                case SERVICE ->
                        sign(
                                null,
                                BOOKING_ISSUER,
                                List.of("hotel-internal"),
                                "booking-service",
                                null,
                                false);
                case MIXED_AUDIENCE ->
                        sign(
                                "USER",
                                BOOKING_ISSUER,
                                List.of(API_AUDIENCE, "hotel-internal"),
                                "17",
                                version,
                                false);
                case ISSUER ->
                        sign(
                                "USER",
                                "http://other-issuer",
                                List.of(API_AUDIENCE),
                                "17",
                                version,
                                false);
                case SUBJECT ->
                        sign("USER", BOOKING_ISSUER, List.of(API_AUDIENCE), "0", version, false);
                case ROLE ->
                        sign(
                                "SERVICE",
                                BOOKING_ISSUER,
                                List.of(API_AUDIENCE),
                                "17",
                                version,
                                false);
                case VERSION ->
                        sign("USER", BOOKING_ISSUER, List.of(API_AUDIENCE), "17", null, false);
            };
        }
    }

    private static KeyPair keyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Path pem(KeyPair key) {
        try {
            Path path = Files.createTempFile("gateway-test-public-", ".pem");
            String encoded =
                    Base64.getMimeEncoder(64, new byte[] {'\n'})
                            .encodeToString(key.getPublic().getEncoded());
            Files.writeString(
                    path,
                    "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n");
            return path;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private record Call(
            String method,
            String path,
            String query,
            String body,
            String traceId,
            String authorization) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private record Reply(int status, String body, Map<String, String> headers) {}

    private static final class Stub implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        private final List<Call> calls = new CopyOnWriteArrayList<>();
        private volatile int authStatus;
        private volatile Reply reply;

        Stub() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException failure) {
                throw new IllegalStateException("Could not bind stub service", failure);
            }
            server.setExecutor(workers);
            server.createContext("/", this::handle);
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void reset() {
            calls.clear();
            authStatus = 204;
            reply = new Reply(200, "{\"forwarded\":true}", Map.of());
        }

        List<Call> authCalls() {
            return calls.stream().filter(call -> call.path().equals(AUTH_CHECK)).toList();
        }

        List<Call> businessCalls() {
            return calls.stream().filter(call -> !call.path().equals(AUTH_CHECK)).toList();
        }

        @Override
        public void close() {
            server.stop(0);
            workers.shutdownNow();
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                String trace = exchange.getRequestHeaders().getFirst(TRACE_HEADER);
                calls.add(
                        new Call(
                                exchange.getRequestMethod(),
                                path,
                                exchange.getRequestURI().getRawQuery(),
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8),
                                trace,
                                exchange.getRequestHeaders().getFirst(AUTHORIZATION)));
                if (path.equals(AUTH_CHECK)) {
                    exchange.sendResponseHeaders(authStatus, -1);
                    return;
                }
                Reply response = reply;
                byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (trace != null) {
                    exchange.getResponseHeaders().set(TRACE_HEADER, trace);
                }
                response.headers().forEach(exchange.getResponseHeaders()::set);
                exchange.sendResponseHeaders(response.status(), body.length);
                exchange.getResponseBody().write(body);
            }
        }
    }
}
