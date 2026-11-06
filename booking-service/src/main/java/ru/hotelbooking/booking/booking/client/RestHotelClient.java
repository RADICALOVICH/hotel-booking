package ru.hotelbooking.booking.booking.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import ru.hotelbooking.booking.security.service.JwtTokenService;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Component
class RestHotelClient implements HotelClient {
    private static final String TOTAL_COUNT = "X-Total-Count";
    private static final Set<String> NO_ACTIVE_ALLOCATION =
            Set.of("ROOM_UNAVAILABLE", "ROOM_NOT_FOUND", "ALLOCATION_RELEASED");
    private static final Set<String> ALLOCATION_CONFLICTS =
            Set.of("ROOM_UNAVAILABLE", "ALLOCATION_MISMATCH", "ALLOCATION_RELEASED");

    private final HttpClient transport;
    private final RestClient hotel;
    private final ObjectMapper json;
    private final JwtTokenService tokens;
    private final Retry retry =
            Retry.of(
                    "hotel",
                    RetryConfig.custom()
                            .maxAttempts(3)
                            .intervalFunction(IntervalFunction.ofExponentialBackoff(200L, 2))
                            .retryOnException(
                                    failure ->
                                            failure instanceof HotelClientException hotelFailure
                                                    && hotelFailure.retryable())
                            .build());

    RestHotelClient(
            RestClient.Builder builder,
            ObjectMapper json,
            JwtTokenService tokens,
            @Value("${booking.hotel.base-url:${BOOKING_HOTEL_URL:http://127.0.0.1:8082}}")
                    String baseUrl,
            @Value("${booking.hotel.connect-timeout:500ms}") Duration connectTimeout,
            @Value("${booking.hotel.read-timeout:1s}") Duration readTimeout) {
        this.transport = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(transport);
        requests.setReadTimeout(readTimeout);
        this.hotel = builder.baseUrl(baseUrl).requestFactory(requests).build();
        this.json = json;
        this.tokens = tokens;
    }

    @PreDestroy
    void close() {
        transport.shutdownNow();
    }

    @Override
    public void confirm(long roomId, AllocationRequest request) {
        call(() -> allocate(roomId, request, "confirm-availability", Set.of("ACTIVE")));
    }

    @Override
    public void release(long roomId, AllocationRequest request) {
        call(() -> releaseAllocation(roomId, request));
    }

    @Override
    public void releaseOnce(long roomId, AllocationRequest request) {
        execute(() -> releaseAllocation(roomId, request));
    }

    private AllocationReply releaseAllocation(long roomId, AllocationRequest request) {
        return allocate(roomId, request, "release", Set.of("RELEASED", "REJECTED"));
    }

    private AllocationReply allocate(
            long roomId, AllocationRequest request, String operation, Set<String> expectedStates) {
        return hotel.post()
                .uri("/api/rooms/{id}/{operation}", roomId, operation)
                .headers(headers -> headers.setBearerAuth(tokens.serviceToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange(
                        (sent, response) ->
                                readAllocation(response, roomId, request, expectedStates));
    }

    private AllocationReply readAllocation(
            ClientHttpResponse response,
            long roomId,
            AllocationRequest request,
            Set<String> expectedStates)
            throws IOException {
        requireOk(response);
        AllocationReply reply = read(response, new TypeReference<>() {});
        if (reply == null
                || !reply.matches(roomId, request)
                || reply.state() == null
                || !expectedStates.contains(reply.state())) {
            throw invalidResponse();
        }
        return reply;
    }

    @Override
    public RecommendedRooms recommend(
            LocalDate startDate,
            LocalDate endDate,
            Long hotelId,
            int page,
            int size,
            String userToken) {
        return call(
                () ->
                        hotel.get()
                                .uri(
                                        uri ->
                                                uri.path("/api/rooms/recommend")
                                                        .queryParam("startDate", startDate)
                                                        .queryParam("endDate", endDate)
                                                        .queryParamIfPresent(
                                                                "hotelId",
                                                                Optional.ofNullable(hotelId))
                                                        .queryParam("page", page)
                                                        .queryParam("size", size)
                                                        .build())
                                .headers(headers -> headers.setBearerAuth(userToken))
                                .exchange((sent, response) -> readRecommendations(response, size)));
    }

    private RecommendedRooms readRecommendations(ClientHttpResponse response, int size)
            throws IOException {
        requireOk(response);
        List<RecommendedRoom> rooms = read(response, new TypeReference<>() {});
        long totalCount = totalCount(response.getHeaders().getFirst(TOTAL_COUNT));
        if (rooms == null
                || rooms.size() > size
                || totalCount < rooms.size()
                || rooms.stream().anyMatch(room -> room == null || room.id() <= 0)) {
            throw invalidResponse();
        }
        return new RecommendedRooms(List.copyOf(rooms), totalCount);
    }

    private static long totalCount(String header) {
        try {
            return Long.parseLong(header);
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private <T> T call(Supplier<T> operation) {
        AtomicBoolean hadRetryableFailure = new AtomicBoolean();
        try {
            return retry.executeSupplier(
                    () -> {
                        try {
                            return execute(operation);
                        } catch (HotelClientException exception) {
                            if (exception.retryable()) {
                                hadRetryableFailure.set(true);
                            }
                            throw exception;
                        }
                    });
        } catch (HotelClientException failure) {
            // A later rejection may describe only that retry, after an earlier call took effect.
            if (hadRetryableFailure.get()
                    && !failure.ambiguous()
                    && !NO_ACTIVE_ALLOCATION.contains(failure.code())) {
                throw new HotelClientException(failure.status(), failure.code(), true, false);
            }
            throw failure;
        }
    }

    private static <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ResourceAccessException exception) {
            if (exception.contains(HttpTimeoutException.class)
                    || exception.contains(SocketTimeoutException.class)) {
                throw new HotelClientException(504, "HOTEL_TIMEOUT", true, true);
            }
            throw new HotelClientException(503, "HOTEL_UNAVAILABLE", true, true);
        }
    }

    private void requireOk(ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.value() == 200) {
            return;
        }
        if (status.is5xxServerError()) {
            throw new HotelClientException(503, "HOTEL_UNAVAILABLE", true, true);
        }
        if (status.is4xxClientError()) {
            String code = errorCode(response);
            throw rejected(status.value(), code);
        }
        throw invalidResponse();
    }

    private String errorCode(ClientHttpResponse response) {
        try {
            JsonNode body = json.readTree(response.getBody().readAllBytes());
            return body == null ? "" : body.path("code").asText("");
        } catch (IOException ignored) {
            return "";
        }
    }

    private static HotelClientException rejected(int status, String code) {
        String knownCode =
                switch (status) {
                    case 400 -> "VALIDATION_FAILED".equals(code) ? code : null;
                    case 401 -> "UNAUTHORIZED";
                    case 403 -> "FORBIDDEN";
                    case 404 -> "ROOM_NOT_FOUND".equals(code) ? code : null;
                    case 409 -> ALLOCATION_CONFLICTS.contains(code) ? code : null;
                    default -> null;
                };
        if (knownCode == null) {
            return new HotelClientException(status, "HOTEL_REQUEST_REJECTED", true, false);
        }
        return new HotelClientException(status, knownCode, false, false);
    }

    private <T> T read(ClientHttpResponse response, TypeReference<T> type) throws IOException {
        byte[] body = response.getBody().readAllBytes();
        try {
            return json.readValue(body, type);
        } catch (IOException exception) {
            throw invalidResponse();
        }
    }

    private static HotelClientException invalidResponse() {
        return new HotelClientException(503, "HOTEL_INVALID_RESPONSE", true, false);
    }

    private record AllocationReply(
            String allocationId,
            long bookingId,
            long roomId,
            LocalDate startDate,
            LocalDate endDate,
            String state) {
        boolean matches(long expectedRoomId, AllocationRequest request) {
            return roomId == expectedRoomId
                    && bookingId == request.bookingId()
                    && request.allocationId().equals(allocationId)
                    && request.startDate().equals(startDate)
                    && request.endDate().equals(endDate);
        }
    }
}
