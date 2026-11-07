package ru.hotelbooking.hotel.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import ru.hotelbooking.hotel.availability.client.RestBookingOccupancy;
import ru.hotelbooking.hotel.availability.exception.BookingOccupancyUnavailableException;
import ru.hotelbooking.hotel.common.client.BookingHttpClient;
import ru.hotelbooking.support.security.OccupancyJwt;
import ru.hotelbooking.support.trace.TraceIds;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class BookingOccupancyClientTest {
    private static final String SECRET = "test-only-occupancy-key-at-least-32-bytes";
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger delay = new AtomicInteger();
    private final AtomicReference<String> body =
            new AtomicReference<>(
                    "[{\"roomId\":42,\"startDate\":\"2026-12-10\",\"endDate\":\"2026-12-12\"}]");
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> trace = new AtomicReference<>();
    private HttpServer server;
    private RestBookingOccupancy client;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                OccupancyJwt.PATH,
                exchange -> {
                    auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    trace.set(exchange.getRequestHeaders().getFirst(TraceIds.HEADER));
                    query.set(exchange.getRequestURI().getQuery());
                    try {
                        Thread.sleep(delay.get());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    try {
                        exchange.sendResponseHeaders(status.get(), bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        client =
                new RestBookingOccupancy(
                        new BookingHttpClient(
                                RestClient.builder(),
                                null,
                                "http://127.0.0.1:" + server.getAddress().getPort(),
                                Duration.ofMillis(500),
                                Duration.ofMillis(200)),
                        SECRET);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readsRealHttpAndSignsANarrowServiceTokenAndPropagatesTrace() {
        String expectedTrace = "00112233445566778899aabbccddeeff";
        try (var scope = TraceIds.open(expectedTrace)) {
            assertThat(client.find(LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 12)))
                    .singleElement()
                    .satisfies(stay -> assertThat(stay.roomId()).isEqualTo(42));
        }
        var decoded =
                OccupancyJwt.decoder(SECRET, Clock.systemUTC()).decode(auth.get().substring(7));
        assertThat(decoded.getSubject()).isEqualTo("hotel-service");
        assertThat(decoded.getAudience()).containsExactly("booking-occupancy");
        assertThat(decoded.getExpiresAt()).isAfter(Clock.systemUTC().instant());
        assertThat(trace.get()).isEqualTo(expectedTrace);
        assertThat(query.get()).contains("startDate=2026-12-10", "endDate=2026-12-12");
    }

    @Test
    void rejectsErrorsMissingBodyAndMalformedRowsInsteadOfReturningFreeRooms() {
        status.set(503);
        fails();
        status.set(200);
        for (String invalid :
                new String[] {
                    "",
                    "null",
                    "[null]",
                    "{}",
                    "[{\"roomId\":0,\"startDate\":\"2026-12-10\",\"endDate\":\"2026-12-12\"}]"
                }) {
            body.set(invalid);
            fails();
        }
    }

    @Test
    void boundsSlowReadsAndDoesNotTreatTimeoutAsAnEmptyCalendar() {
        delay.set(2000);
        long started = System.nanoTime();
        fails();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
    }

    private void fails() {
        assertThatThrownBy(
                        () -> client.find(LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 12)))
                .isInstanceOf(BookingOccupancyUnavailableException.class);
    }
}
