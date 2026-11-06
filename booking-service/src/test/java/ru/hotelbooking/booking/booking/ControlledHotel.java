package ru.hotelbooking.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

final class ControlledHotel implements AutoCloseable {
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final List<RuntimeException> failures = new CopyOnWriteArrayList<>();
    private volatile Function<Call, Reply> handler;

    ControlledHotel() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not start Hotel test server", failure);
        }
        server.setExecutor(workers);
        server.createContext("/api/rooms/", this::handle);
        reset();
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void reset() {
        calls.clear();
        failures.clear();
        respondWith(call -> new Reply(500, Map.of("code", "UNSCRIPTED_TEST_CALL")));
    }

    void respondWith(Function<Call, Reply> handler) {
        this.handler = handler;
    }

    List<Call> calls() {
        return List.copyOf(calls);
    }

    List<Call> confirms() {
        return calls.stream().filter(Call::isConfirm).toList();
    }

    List<Call> releases() {
        return calls.stream().filter(Call::isRelease).toList();
    }

    void assertHealthy() {
        assertThat(failures).isEmpty();
    }

    @Override
    public void close() {
        server.stop(0);
        workers.shutdownNow();
    }

    private void handle(HttpExchange exchange) {
        try (exchange) {
            Call call =
                    new Call(
                            exchange.getRequestMethod(),
                            exchange.getRequestURI().getPath(),
                            exchange.getRequestURI().getQuery(),
                            json.readTree(exchange.getRequestBody()),
                            exchange.getRequestHeaders().getFirst("Authorization"),
                            exchange.getRequestHeaders().getFirst("X-Trace-Id"),
                            System.nanoTime());
            calls.add(call);
            Reply reply = handler.apply(call);
            if (!reply.delay().isZero()) {
                Thread.sleep(reply.delay());
            }
            if (reply.total() != null) {
                exchange.getResponseHeaders().set("X-Total-Count", reply.total().toString());
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] body = json.writeValueAsBytes(reply.body());
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
        } catch (IOException ignored) {
            // A caller that times out can close the connection before the scripted reply.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failure) {
            failures.add(failure);
        }
    }

    record Call(
            String method,
            String path,
            String query,
            JsonNode body,
            String authorization,
            String traceId,
            long receivedAt) {
        boolean isConfirm() {
            return path.endsWith("/confirm-availability");
        }

        boolean isRelease() {
            return path.endsWith("/release");
        }

        long roomId() {
            return Long.parseLong(path.split("/")[3]);
        }

        String allocationId() {
            return body.path("allocationId").asText();
        }

        Reply allocation(String state) {
            return new Reply(
                    200,
                    Map.of(
                            "state", state,
                            "roomId", roomId(),
                            "bookingId", body.path("bookingId").asLong(),
                            "allocationId", allocationId(),
                            "startDate", body.path("startDate").asText(),
                            "endDate", body.path("endDate").asText()));
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    record Reply(int status, Object body, Integer total, Duration delay) {
        Reply(int status, Object body) {
            this(status, body, null, Duration.ZERO);
        }

        static Reply recommendations(List<Map<String, Object>> rooms) {
            return new Reply(200, rooms, rooms.size(), Duration.ZERO);
        }

        static Reply error(int status, String code) {
            return new Reply(status, Map.of("code", code));
        }

        Reply delayed(Duration duration) {
            return new Reply(status, body, total, duration);
        }
    }
}
