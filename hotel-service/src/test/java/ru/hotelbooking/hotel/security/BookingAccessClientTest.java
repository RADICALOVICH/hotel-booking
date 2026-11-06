package ru.hotelbooking.hotel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import ru.hotelbooking.hotel.security.client.BookingAccessClient;
import ru.hotelbooking.hotel.security.exception.AccessCheckUnavailableException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Talks to a real local HTTP server, so timeouts and status handling are exercised for real. */
class BookingAccessClientTest {
    private static final Duration CONNECT = Duration.ofMillis(500);
    private static final Duration READ = Duration.ofMillis(300);

    private final AtomicInteger status = new AtomicInteger(204);
    private final AtomicInteger delayMillis = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private HttpServer server;
    private BookingAccessClient client;

    @BeforeEach
    void startFakeBooking() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/internal/auth/validate",
                exchange -> {
                    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    sleep(delayMillis.get());
                    exchange.sendResponseHeaders(status.get(), -1);
                    exchange.close();
                });
        server.start();
        client = clientFor("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopFakeBooking() {
        server.stop(0);
    }

    @Test
    void mapsBookingAnswersToCurrentRevokedOrUnavailable() {
        assertThat(client.isCurrent("token-a")).isTrue();
        assertThat(authorization.get()).isEqualTo("Bearer token-a");

        status.set(401);
        assertThat(client.isCurrent("token-a")).isFalse();

        status.set(500);
        assertThatThrownBy(() -> client.isCurrent("token-a"))
                .isInstanceOf(AccessCheckUnavailableException.class)
                .hasMessageContaining("500");
    }

    @Test
    void slowBookingFailsWithinReadTimeout() {
        delayMillis.set(2_000);
        long started = System.nanoTime();

        assertThatThrownBy(() -> client.isCurrent("token-a"))
                .isInstanceOf(AccessCheckUnavailableException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .isLessThan(Duration.ofMillis(1_500));
    }

    @Test
    void stoppedBookingIsReportedAsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> clientFor("http://127.0.0.1:" + closedPort).isCurrent("token-a"))
                .isInstanceOf(AccessCheckUnavailableException.class);
    }

    private static BookingAccessClient clientFor(String baseUrl) {
        return new BookingAccessClient(RestClient.builder(), null, baseUrl, CONNECT, READ);
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
