package ru.hotelbooking.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.hotelbooking.booking.booking.ControlledHotel.Call;
import ru.hotelbooking.booking.booking.ControlledHotel.Reply;
import ru.hotelbooking.booking.booking.service.BookingRecovery;
import ru.hotelbooking.booking.security.TestJwtKeys;
import ru.hotelbooking.booking.support.TestAccounts;
import ru.hotelbooking.booking.support.TestAccounts.Account;
import ru.hotelbooking.booking.user.service.UserService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(properties = "booking.compensation.poll-delay-ms=3600000")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(TestJwtKeys.class)
@ActiveProfiles("test")
class BookingSagaTest {
    private static final ControlledHotel HOTEL = new ControlledHotel();
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final BookingRecovery recovery;
    private final CircuitBreakerRegistry circuits;
    private final TestAccounts accounts;
    private Account guest;

    @Autowired
    BookingSagaTest(
            MockMvc mvc,
            ObjectMapper json,
            JdbcTemplate jdbc,
            BookingRecovery recovery,
            CircuitBreakerRegistry circuits,
            UserService users) {
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
        this.recovery = recovery;
        this.circuits = circuits;
        this.accounts = new TestAccounts(mvc, json, users);
    }

    @DynamicPropertySource
    static void connectHotel(DynamicPropertyRegistry properties) {
        properties.add("booking.hotel.base-url", HOTEL::url);
    }

    @BeforeEach
    void prepareHotelAndGuest() throws Exception {
        HOTEL.reset();
        circuits.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        guest = accounts.user(name());
    }

    @AfterEach
    void checkHotelScript() {
        HOTEL.assertHealthy();
    }

    @AfterAll
    static void stopHotel() {
        HOTEL.close();
    }

    @Test
    void commitsPendingBeforeHttpAndRetriesOfTheClientGetTheSameBooking() throws Exception {
        Stay stay = Stay.manual(7);
        CountDownLatch enteredHotel = new CountDownLatch(1);
        CountDownLatch allowConfirm = new CountDownLatch(1);
        HOTEL.respondWith(
                call -> {
                    enteredHotel.countDown();
                    await(allowConfirm);
                    return call.allocation("ACTIVE");
                });

        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MockHttpServletResponse> create =
                    worker.submit(() -> response(create(guest, stay)));
            try {
                await(enteredHotel);
                Call confirm = HOTEL.confirms().getFirst();
                long id = confirm.body().path("bookingId").asLong();
                assertThat(state(id)).isEqualTo("PENDING");
                assertThat(confirm.roomId()).isEqualTo(7);
                assertThat(confirm.allocationId()).isNotBlank();
                assertThat(confirm.body().path("startDate").asText())
                        .isEqualTo(stay.startDate().toString());
                assertThat(confirm.body().path("endDate").asText())
                        .isEqualTo(stay.endDate().toString());
                assertThat(
                                confirm.authorization() != null
                                        && !confirm.authorization().equals(guest.bearer()))
                        .isTrue();
                perform(guest, get("/booking/{id}", id))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("PENDING"));
                create(guest, stay)
                        .andExpect(status().isAccepted())
                        .andExpect(header().string("Location", "/booking/" + id))
                        .andExpect(jsonPath("$.id").value(id));
            } finally {
                allowConfirm.countDown();
            }

            MockHttpServletResponse created = create.get(5, TimeUnit.SECONDS);
            assertThat(created.getStatus()).isEqualTo(201);
            long id = id(created, "id");
            assertThat(created.getHeader("Location")).isEqualTo("/booking/" + id);
            assertThat(state(id)).isEqualTo("CONFIRMED");
            create(guest, stay)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }
        assertThat(HOTEL.confirms()).hasSize(1);
        assertThat(bookingCount(guest, stay)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"409, ROOM_UNAVAILABLE", "404, ROOM_NOT_FOUND"})
    void definitiveRejectionCancelsWithoutRepeatingOrReleasing(int status, String code)
            throws Exception {
        Stay stay = Stay.manual(7);
        HOTEL.respondWith(call -> Reply.error(status, code));

        create(guest, stay)
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
        create(guest, stay)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.compensationPending").value(false));
        assertThat(HOTEL.confirms()).hasSize(1);
        assertThat(HOTEL.releases()).isEmpty();
    }

    @Test
    void forwardsRecommendationFiltersAndUsesNextCandidateAfterAnOccupiedRoom() throws Exception {
        List<Map<String, Object>> rooms = List.of(room(9, 0), room(3, 1));
        Stay stay = Stay.auto(-10L, 8L);
        HOTEL.respondWith(
                call -> {
                    if (!call.isConfirm()) {
                        return Reply.recommendations(rooms);
                    }
                    return call.roomId() == 9
                            ? Reply.error(409, "ROOM_UNAVAILABLE")
                            : call.allocation("ACTIVE");
                });

        perform(
                        guest,
                        get("/rooms/recommend")
                                .param("startDate", stay.startDate().toString())
                                .param("endDate", stay.endDate().toString())
                                .param("hotelId", "8")
                                .param("page", "1")
                                .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[*].id", contains(9, 3)));
        Call recommendation = HOTEL.calls().getFirst();
        assertThat(recommendation.method()).isEqualTo("GET");
        assertThat(recommendation.path()).isEqualTo("/api/rooms/recommend");
        assertThat(recommendation.query().split("&"))
                .containsExactlyInAnyOrder(
                        "startDate=" + stay.startDate(),
                        "endDate=" + stay.endDate(),
                        "hotelId=8",
                        "page=1",
                        "size=5");
        assertThat(guest.bearer().equals(recommendation.authorization())).isTrue();

        create(guest, stay)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(3));
        create(guest, stay.withRoom(777))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(3));
        assertThat(HOTEL.confirms()).extracting(Call::roomId).containsExactly(9L, 3L);
        assertThat(HOTEL.confirms()).extracting(Call::allocationId).doesNotHaveDuplicates();
        assertThat(HOTEL.releases()).isEmpty();
    }

    @Test
    void anEmptyRecommendationListCancelsWithoutContactingAnAllocationEndpoint() throws Exception {
        HOTEL.respondWith(call -> Reply.recommendations(List.of()));
        Stay stay = Stay.auto(null, null);

        create(guest, stay)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ROOMS_AVAILABLE"));
        create(guest, stay)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(HOTEL.calls()).hasSize(1);
    }

    @Test
    void autoSelectionStopsAfterTenDefinitivelyRejectedCandidates() throws Exception {
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (int roomId = 1; roomId <= 10; roomId++) {
            rooms.add(room(roomId, 0));
        }
        HOTEL.respondWith(
                call ->
                        call.isConfirm()
                                ? Reply.error(409, "ROOM_UNAVAILABLE")
                                : new Reply(200, rooms, 11, Duration.ZERO));

        create(guest, Stay.auto(null, 8L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ROOMS_AVAILABLE"));

        assertThat(HOTEL.calls().getFirst().query().split("&")).contains("size=10");
        assertThat(HOTEL.confirms())
                .extracting(Call::roomId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(HOTEL.calls()).hasSize(11);
    }

    @Test
    void invalidDatesAndMissingManualRoomFailBeforeContactingHotel() throws Exception {
        LocalDate start = future();
        Stay[] invalid = {
            new Stay(UUID.randomUUID(), false, null, start, start.plusDays(1), null),
            new Stay(UUID.randomUUID(), false, 7L, start, start, null),
            new Stay(UUID.randomUUID(), false, 7L, start, start.plusDays(31), null),
            new Stay(
                    UUID.randomUUID(),
                    false,
                    7L,
                    start.plusYears(1),
                    start.plusYears(1).plusDays(1),
                    null),
            new Stay(UUID.randomUUID(), false, 7L, start.minusDays(8), start, null)
        };
        for (Stay stay : invalid) {
            create(guest, stay)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            assertThat(bookingCount(guest, stay)).isZero();
        }
        assertThat(HOTEL.calls()).isEmpty();
    }

    @Test
    void onlyOwnerCanCancelAndRepeatedCancellationAndCreationDoNotAllocateAgain() throws Exception {
        Stay stay = Stay.manual(7);
        HOTEL.respondWith(BookingSagaTest::allocate);
        long id = id(response(create(guest, stay).andExpect(status().isCreated())), "id");
        Account other = accounts.user(name());

        perform(other, delete("/booking/{id}", id)).andExpect(status().isNotFound());
        for (int attempt = 0; attempt < 2; attempt++) {
            perform(guest, delete("/booking/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.compensationPending").value(false));
        }
        create(guest, stay)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(HOTEL.confirms()).hasSize(1);
        assertThat(HOTEL.releases())
                .extracting(Call::allocationId)
                .containsExactly(HOTEL.confirms().getFirst().allocationId());
    }

    @Test
    void cancellationOnCheckInDateLeavesBookingAndAllocationIntact() throws Exception {
        LocalDate today = LocalDate.now(MOSCOW);
        Stay stay = new Stay(UUID.randomUUID(), false, 7L, today, today.plusDays(1), null);
        HOTEL.respondWith(BookingSagaTest::allocate);
        long id = id(response(create(guest, stay).andExpect(status().isCreated())), "id");

        perform(guest, delete("/booking/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_TOO_LATE"));
        assertThat(state(id)).isEqualTo("CONFIRMED");
        assertThat(HOTEL.releases()).isEmpty();
    }

    @Test
    void failsAfterExactlyThreeServerErrorsAndClearsThePersistedCompensation() throws Exception {
        Stay stay = Stay.manual(7);
        HOTEL.respondWith(
                call ->
                        call.isConfirm()
                                ? Reply.error(500, "TEMPORARY_FAILURE")
                                : call.allocation("RELEASED"));

        long id =
                id(
                        response(
                                create(guest, stay)
                                        .andExpect(status().isServiceUnavailable())
                                        .andExpect(jsonPath("$.code").value("HOTEL_UNAVAILABLE"))),
                        "bookingId");

        assertThat(HOTEL.confirms()).hasSize(3);
        assertThat(HOTEL.confirms())
                .extracting(Call::allocationId)
                .containsOnly(HOTEL.confirms().getFirst().allocationId());
        assertThat(HOTEL.releases()).hasSize(1);
        assertThat(state(id)).isEqualTo("CANCELLED");
        assertThat(compensationPending(id)).isFalse();
    }

    @Test
    void lostConfirmResponsesAfterAllocationStopAutoSelectionAndReleaseTheSameAllocation()
            throws Exception {
        Set<String> activeAllocations = ConcurrentHashMap.newKeySet();
        Stay stay = Stay.auto(null, 8L);
        HOTEL.respondWith(
                call -> {
                    if (call.isConfirm()) {
                        activeAllocations.add(call.allocationId());
                        return call.allocation("ACTIVE").delayed(Duration.ofSeconds(2));
                    }
                    if (call.isRelease()) {
                        activeAllocations.remove(call.allocationId());
                        return call.allocation("RELEASED");
                    }
                    return Reply.recommendations(List.of(room(7, 0), room(8, 0)));
                });

        long started = System.nanoTime();
        long id =
                id(
                        response(
                                create(guest, stay)
                                        .andExpect(status().isGatewayTimeout())
                                        .andExpect(jsonPath("$.code").value("HOTEL_TIMEOUT"))),
                        "bookingId");

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(7));
        assertThat(HOTEL.confirms()).extracting(Call::roomId).containsExactly(7L, 7L, 7L);
        assertThat(HOTEL.confirms())
                .extracting(Call::allocationId)
                .containsOnly(HOTEL.confirms().getFirst().allocationId());
        assertThat(HOTEL.releases())
                .extracting(Call::allocationId)
                .containsExactly(HOTEL.confirms().getFirst().allocationId());
        assertThat(activeAllocations).isEmpty();
        assertThat(state(id)).isEqualTo("CANCELLED");
        assertThat(compensationPending(id)).isFalse();
    }

    @Test
    void retryRejectionDoesNotHideAnAllocationFromAnEarlierAmbiguousAttempt() throws Exception {
        AtomicInteger confirms = new AtomicInteger();
        Set<String> activeAllocations = ConcurrentHashMap.newKeySet();
        HOTEL.respondWith(
                call -> {
                    if (call.isRelease()) {
                        activeAllocations.remove(call.allocationId());
                        return call.allocation("RELEASED");
                    }
                    if (confirms.incrementAndGet() == 1) {
                        activeAllocations.add(call.allocationId());
                        return Reply.error(500, "TEMPORARY_FAILURE");
                    }
                    return Reply.error(401, "UNAUTHORIZED");
                });

        long id =
                id(
                        response(
                                create(guest, Stay.manual(7))
                                        .andExpect(status().isServiceUnavailable())
                                        .andExpect(
                                                jsonPath("$.code")
                                                        .value("HOTEL_INVALID_RESPONSE"))),
                        "bookingId");

        assertThat(HOTEL.confirms()).hasSize(2);
        assertThat(HOTEL.releases()).hasSize(1);
        assertThat(activeAllocations).isEmpty();
        assertThat(state(id)).isEqualTo("CANCELLED");
        assertThat(compensationPending(id)).isFalse();
    }

    @Test
    void localConfirmationFailureCancelsAndReleasesAfterHotelAllocated() throws Exception {
        Set<String> activeAllocations = ConcurrentHashMap.newKeySet();
        HOTEL.respondWith(
                call -> {
                    if (call.isConfirm()) {
                        activeAllocations.add(call.allocationId());
                        return call.allocation("ACTIVE");
                    }
                    activeAllocations.remove(call.allocationId());
                    return call.allocation("RELEASED");
                });

        jdbc.execute(
                "alter table bookings add constraint test_reject_confirmation"
                        + " check (status <> 'CONFIRMED' or user_id <> "
                        + guest.id()
                        + ")");
        try {
            long id =
                    id(
                            response(
                                    create(guest, Stay.manual(7))
                                            .andExpect(status().isServiceUnavailable())
                                            .andExpect(
                                                    jsonPath("$.code")
                                                            .value("BOOKING_CONFIRMATION_FAILED"))),
                            "bookingId");

            assertThat(state(id)).isEqualTo("CANCELLED");
            assertThat(compensationPending(id)).isFalse();
            assertThat(HOTEL.confirms()).hasSize(1);
            assertThat(HOTEL.releases()).hasSize(1);
            assertThat(activeAllocations).isEmpty();
        } finally {
            jdbc.execute("alter table bookings drop constraint test_reject_confirmation");
        }
    }

    @Test
    void transientErrorsBackOffAndCanStillConfirmOnTheFinalAttempt() throws Exception {
        AtomicInteger confirms = new AtomicInteger();
        HOTEL.respondWith(
                call ->
                        confirms.incrementAndGet() < 3
                                ? Reply.error(503, "TEMPORARY_FAILURE")
                                : call.allocation("ACTIVE"));

        create(guest, Stay.manual(7))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        List<Call> attempts = HOTEL.confirms();
        assertThat(attempts).hasSize(3);
        assertThat(Duration.ofNanos(attempts.get(1).receivedAt() - attempts.get(0).receivedAt()))
                .isGreaterThanOrEqualTo(Duration.ofMillis(180));
        assertThat(Duration.ofNanos(attempts.get(2).receivedAt() - attempts.get(1).receivedAt()))
                .isGreaterThanOrEqualTo(Duration.ofMillis(380));
        assertThat(HOTEL.releases()).isEmpty();
    }

    @Test
    void concurrentIdenticalPostsCreateOneCoordinator() throws Exception {
        Stay stay = Stay.manual(7);
        HOTEL.respondWith(call -> call.allocation("ACTIVE").delayed(Duration.ofMillis(250)));
        int requests = 8;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<MockHttpServletResponse>> responses = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                responses.add(
                        workers.submit(
                                () -> {
                                    ready.countDown();
                                    await(start);
                                    return response(create(guest, stay));
                                }));
            }
            await(ready);
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : responses) {
                MockHttpServletResponse response = future.get(5, TimeUnit.SECONDS);
                statuses.add(response.getStatus());
                ids.add(id(response, "id"));
            }
            assertThat(statuses).allMatch(Set.of(201, 202, 200)::contains);
            assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
            assertThat(ids).containsOnly(ids.getFirst());
            assertThat(ids.getFirst()).isPositive();
        }
        assertThat(HOTEL.confirms()).hasSize(1);
        assertThat(bookingCount(guest, stay)).isEqualTo(1);
    }

    @Test
    void requestIdIsScopedToOwnerAndConflictsForChangedInput() throws Exception {
        Stay stay = Stay.manual(7);
        Account other = accounts.user(name());
        HOTEL.respondWith(BookingSagaTest::allocate);

        long guestId = id(response(create(guest, stay).andExpect(status().isCreated())), "id");
        create(guest, stay.withRoom(8))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ID_CONFLICT"));
        long otherId = id(response(create(other, stay).andExpect(status().isCreated())), "id");

        assertThat(otherId).isNotEqualTo(guestId);
        assertThat(HOTEL.confirms()).hasSize(2);
        assertThat(bookingCount(guest, stay)).isEqualTo(1);
        assertThat(bookingCount(other, stay)).isEqualTo(1);
    }

    @Test
    void cancellationWinsAgainstAnAlreadySuccessfulConfirmWhoseResponseIsDelayed()
            throws Exception {
        assertRecoveryWinsAgainstDelayedConfirm(false);
    }

    @Test
    void stalePendingRecoveryWinsAgainstAnAlreadySuccessfulConfirmWhoseResponseIsDelayed()
            throws Exception {
        assertRecoveryWinsAgainstDelayedConfirm(true);
    }

    @Test
    void stalePendingWithoutCandidateIsCancelledBeforeAnyAllocationIsRequested() throws Exception {
        CountDownLatch recommending = new CountDownLatch(1);
        CountDownLatch allowRecommendation = new CountDownLatch(1);
        HOTEL.respondWith(
                call -> {
                    recommending.countDown();
                    await(allowRecommendation);
                    return Reply.recommendations(List.of(room(7, 0)));
                });
        Stay stay = Stay.auto(null, 8L);

        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MockHttpServletResponse> create =
                    worker.submit(() -> response(create(guest, stay)));
            long id;
            try {
                await(recommending);
                id =
                        jdbc.queryForObject(
                                "select id from bookings where user_id = ? and request_id = ?",
                                Long.class,
                                guest.id(),
                                stay.requestId());
                jdbc.update("update bookings set updated_at = ? where id = ?", due(), id);
                recovery.run();
            } finally {
                allowRecommendation.countDown();
            }

            MockHttpServletResponse result = create.get(5, TimeUnit.SECONDS);
            assertThat(result.getStatus()).isEqualTo(409);
            assertThat(state(id)).isEqualTo("CANCELLED");
            assertThat(compensationPending(id)).isFalse();
        }
        assertThat(HOTEL.calls()).hasSize(1);
    }

    private void assertRecoveryWinsAgainstDelayedConfirm(boolean stale) throws Exception {
        Stay stay = Stay.manual(7);
        Set<String> activeAllocations = ConcurrentHashMap.newKeySet();
        CountDownLatch allocated = new CountDownLatch(1);
        CountDownLatch allowConfirmReply = new CountDownLatch(1);
        HOTEL.respondWith(
                call -> {
                    if (call.isConfirm()) {
                        activeAllocations.add(call.allocationId());
                        allocated.countDown();
                        await(allowConfirmReply);
                        return call.allocation("ACTIVE");
                    }
                    activeAllocations.remove(call.allocationId());
                    return call.allocation("RELEASED");
                });

        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MockHttpServletResponse> create =
                    worker.submit(() -> response(create(guest, stay)));
            long id;
            try {
                await(allocated);
                id = HOTEL.confirms().getFirst().body().path("bookingId").asLong();
                if (stale) {
                    jdbc.update("update bookings set updated_at = ? where id = ?", due(), id);
                    recovery.run();
                } else {
                    perform(guest, delete("/booking/{id}", id))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value("CANCELLED"));
                }
                assertThat(state(id)).isEqualTo("CANCELLED");
            } finally {
                allowConfirmReply.countDown();
            }
            MockHttpServletResponse result = create.get(5, TimeUnit.SECONDS);
            assertThat(result.getStatus()).isEqualTo(409);
            assertThat(json.readTree(result.getContentAsByteArray()).path("code").asText())
                    .isEqualTo("BOOKING_CANCELLED");
            assertThat(state(id)).isEqualTo("CANCELLED");
            assertThat(compensationPending(id)).isFalse();
        }
        assertThat(HOTEL.confirms()).hasSize(1);
        assertThat(HOTEL.releases()).isNotEmpty();
        assertThat(activeAllocations).isEmpty();
    }

    @Test
    void unsuccessfulReleaseRemainsPendingAndRecoveryClearsItWhenHotelReturns() throws Exception {
        String cancellationTrace = "0123456789abcdef0123456789abcdef";
        AtomicBoolean hotelReady = new AtomicBoolean(false);
        Set<String> activeAllocations = ConcurrentHashMap.newKeySet();
        CountDownLatch retryingRelease = new CountDownLatch(1);
        CountDownLatch allowReleaseReply = new CountDownLatch(1);
        HOTEL.respondWith(
                call -> {
                    if (call.isConfirm()) {
                        activeAllocations.add(call.allocationId());
                        return call.allocation("ACTIVE");
                    }
                    if (!hotelReady.get()) {
                        return Reply.error(503, "UNAVAILABLE");
                    }
                    activeAllocations.remove(call.allocationId());
                    retryingRelease.countDown();
                    await(allowReleaseReply);
                    return call.allocation("RELEASED");
                });
        long id = id(response(create(guest, Stay.manual(7)).andExpect(status().isCreated())), "id");

        perform(guest, delete("/booking/{id}", id).header("X-Trace-Id", cancellationTrace))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", cancellationTrace))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.compensationPending").value(true));
        assertThat(HOTEL.releases()).hasSize(3);
        assertThat(activeAllocations).hasSize(1);
        assertThat(attempts(id)).isEqualTo(1);

        hotelReady.set(true);
        makeCompensationDue(id);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> retry =
                    worker.submit(
                            () -> {
                                recovery.run();
                                assertThat(MDC.get("traceId")).isNull();
                            });
            try {
                await(retryingRelease);
                recovery.run();
                assertThat(HOTEL.releases()).hasSize(4);
            } finally {
                allowReleaseReply.countDown();
            }
            retry.get(5, TimeUnit.SECONDS);
        }

        assertThat(compensationPending(id)).isFalse();
        assertThat(HOTEL.releases()).hasSize(4);
        assertThat(HOTEL.confirms().getFirst().traceId())
                .matches("[0-9a-f]{32}")
                .isNotEqualTo(cancellationTrace);
        assertThat(HOTEL.releases()).extracting(Call::traceId).containsOnly(cancellationTrace);
        assertThat(activeAllocations).isEmpty();
    }

    @Test
    void exhaustedCompensationStaysPendingUntilAdminRestartsIt() throws Exception {
        // This test makes each retry due immediately, independently of the real circuit cooldown.
        circuits.circuitBreaker("hotel-release").transitionToDisabledState();
        AtomicBoolean hotelReady = new AtomicBoolean(false);
        HOTEL.respondWith(
                call ->
                        call.isConfirm() || hotelReady.get()
                                ? allocate(call)
                                : Reply.error(503, "UNAVAILABLE"));
        long id = id(response(create(guest, Stay.manual(7)).andExpect(status().isCreated())), "id");
        perform(guest, delete("/booking/{id}", id)).andExpect(status().isOk());
        assertThat(HOTEL.releases()).hasSize(3);
        assertThat(scheduledBackoff(id)).isEqualTo(Duration.ofSeconds(1));

        for (int attempt = 2; attempt <= 10; attempt++) {
            makeCompensationDue(id);
            recovery.run();
            assertThat(attempts(id)).isEqualTo(attempt);
            assertThat(HOTEL.releases()).hasSize(attempt + 2);
            if (attempt < 10) {
                assertThat(scheduledBackoff(id))
                        .isEqualTo(Duration.ofSeconds(Math.min(30, 1L << (attempt - 1))));
            }
        }
        recovery.run();
        assertThat(compensationPending(id)).isTrue();
        assertThat(scheduledBackoff(id)).isNull();
        assertThat(HOTEL.releases()).hasSize(12);

        perform(guest, post("/booking/{id}/compensation", id)).andExpect(status().isForbidden());
        Account admin = accounts.admin(name());
        hotelReady.set(true);
        perform(admin, post("/booking/{id}/compensation", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compensationPending").value(false));
        assertThat(HOTEL.releases()).hasSize(13);
        assertThat(compensationPending(id)).isFalse();
    }

    @Test
    void confirmBreakerIgnoresConflictsOpensAtThresholdAndRecoversWithThreeProbes()
            throws Exception {
        var confirm = circuits.circuitBreaker("hotel-confirm");
        var release = circuits.circuitBreaker("hotel-release");
        Set<String> activeAllocations = ConcurrentHashMap.newKeySet();
        HOTEL.respondWith(
                call -> {
                    if (call.isRelease()) {
                        activeAllocations.remove(call.allocationId());
                        return call.allocation("RELEASED");
                    }
                    return switch ((int) call.roomId()) {
                        case 7 -> call.allocation("ACTIVE");
                        case 8 -> Reply.error(409, "ROOM_UNAVAILABLE");
                        case 9 -> Reply.error(503, "UNAVAILABLE");
                        default -> {
                            activeAllocations.add(call.allocationId());
                            yield call.allocation("ACTIVE").delayed(Duration.ofSeconds(2));
                        }
                    };
                });

        for (int i = 0; i < 5; i++) {
            create(guest, Stay.manual(7)).andExpect(status().isCreated());
        }
        create(guest, Stay.manual(9))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("HOTEL_UNAVAILABLE"));
        for (int i = 0; i < 3; i++) {
            create(guest, Stay.manual(8)).andExpect(status().isConflict());
        }
        assertThat(confirm.getMetrics().getNumberOfBufferedCalls()).isEqualTo(8);
        assertThat(confirm.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        long timedOutId =
                id(
                        response(
                                create(guest, Stay.manual(10))
                                        .andExpect(status().isGatewayTimeout())
                                        .andExpect(jsonPath("$.code").value("HOTEL_TIMEOUT"))),
                        "bookingId");
        assertThat(confirm.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(confirm.getMetrics().getFailureRate()).isEqualTo(50);
        assertThat(HOTEL.confirms())
                .extracting(Call::roomId)
                .containsExactly(7L, 7L, 7L, 7L, 7L, 9L, 9L, 9L, 8L, 8L, 8L, 10L, 10L);
        assertThat(HOTEL.releases()).extracting(Call::roomId).containsExactly(9L, 10L);
        assertThat(release.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(activeAllocations).isEmpty();
        assertThat(state(timedOutId)).isEqualTo("CANCELLED");
        assertThat(compensationPending(timedOutId)).isFalse();

        long started = System.nanoTime();
        long skippedId =
                id(
                        response(
                                create(guest, Stay.manual(7))
                                        .andExpect(status().isServiceUnavailable())
                                        .andExpect(jsonPath("$.code").value("HOTEL_CIRCUIT_OPEN"))),
                        "bookingId");
        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .isLessThan(Duration.ofMillis(500));
        assertThat(HOTEL.confirms()).hasSize(13);
        assertThat(HOTEL.releases()).hasSize(2);
        assertThat(state(skippedId)).isEqualTo("CANCELLED");
        assertThat(compensationPending(skippedId)).isFalse();

        assertThat(confirm.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(10_000L);
        confirm.transitionToHalfOpenState();
        for (int probe = 1; probe <= 3; probe++) {
            create(guest, Stay.manual(7)).andExpect(status().isCreated());
            assertThat(confirm.getState())
                    .isEqualTo(
                            probe < 3
                                    ? CircuitBreaker.State.HALF_OPEN
                                    : CircuitBreaker.State.CLOSED);
        }
        assertThat(HOTEL.confirms()).hasSize(16);
    }

    @Test
    void openReleaseBreakerKeepsCompensationPendingUntilItAllowsAProbe() throws Exception {
        var release = circuits.circuitBreaker("hotel-release");
        HOTEL.respondWith(BookingSagaTest::allocate);
        long id = id(response(create(guest, Stay.manual(7)).andExpect(status().isCreated())), "id");
        release.transitionToOpenState();

        perform(guest, delete("/booking/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.compensationPending").value(true));
        assertThat(HOTEL.releases()).isEmpty();
        assertThat(attempts(id)).isEqualTo(1);

        create(guest, Stay.manual(7)).andExpect(status().isCreated());
        makeCompensationDue(id);
        recovery.run();
        assertThat(HOTEL.releases()).isEmpty();
        assertThat(compensationPending(id)).isTrue();

        release.transitionToHalfOpenState();
        makeCompensationDue(id);
        recovery.run();
        assertThat(HOTEL.releases()).hasSize(1);
        assertThat(compensationPending(id)).isFalse();
    }

    @Test
    void boundsTheResponseWhenConfirmAndImmediateReleaseBothTimeOut() throws Exception {
        HOTEL.respondWith(call -> allocate(call).delayed(Duration.ofSeconds(2)));

        long started = System.nanoTime();
        long id =
                id(
                        response(
                                create(guest, Stay.manual(7))
                                        .andExpect(status().isGatewayTimeout())
                                        .andExpect(jsonPath("$.code").value("HOTEL_TIMEOUT"))),
                        "bookingId");

        try {
            assertThat(Duration.ofNanos(System.nanoTime() - started))
                    .isBetween(Duration.ofSeconds(7), Duration.ofSeconds(10));
            assertThat(HOTEL.confirms()).hasSize(3);
            assertThat(HOTEL.releases()).hasSize(3);
            assertThat(state(id)).isEqualTo("CANCELLED");
            assertThat(compensationPending(id)).isTrue();
        } finally {
            HOTEL.respondWith(BookingSagaTest::allocate);
            makeCompensationDue(id);
            recovery.run();
        }
        assertThat(compensationPending(id)).isFalse();
    }

    private static Reply allocate(Call call) {
        return call.allocation(call.isConfirm() ? "ACTIVE" : "RELEASED");
    }

    private ResultActions create(Account account, Stay stay) throws Exception {
        return perform(
                account,
                post("/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(stay)));
    }

    private ResultActions perform(Account account, MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, account.bearer()));
    }

    private static MockHttpServletResponse response(ResultActions result) {
        return result.andReturn().getResponse();
    }

    private long id(MockHttpServletResponse response, String field) throws Exception {
        return json.readTree(response.getContentAsByteArray()).path(field).asLong();
    }

    private String state(long id) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, id);
    }

    private boolean compensationPending(long id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select compensation_pending from bookings where id = ?",
                        Boolean.class,
                        id));
    }

    private int attempts(long id) {
        return jdbc.queryForObject(
                "select compensation_attempts from bookings where id = ?", Integer.class, id);
    }

    private Duration scheduledBackoff(long id) {
        return jdbc.queryForObject(
                "select updated_at, next_compensation_at from bookings where id = ?",
                (row, index) -> {
                    var next = row.getObject("next_compensation_at", OffsetDateTime.class);
                    return next == null
                            ? null
                            : Duration.between(
                                    row.getObject("updated_at", OffsetDateTime.class), next);
                },
                id);
    }

    private int bookingCount(Account account, Stay stay) {
        return jdbc.queryForObject(
                "select count(*) from bookings where user_id = ? and request_id = ?",
                Integer.class,
                account.id(),
                stay.requestId());
    }

    private void makeCompensationDue(long id) {
        jdbc.update("update bookings set next_compensation_at = ? where id = ?", due(), id);
    }

    private static OffsetDateTime due() {
        return OffsetDateTime.now().minusMinutes(5);
    }

    private static void await(CountDownLatch gate) {
        try {
            if (!gate.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the test gate");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for the test gate", interrupted);
        }
    }

    private static String name() {
        return "saga-" + NAMES.incrementAndGet();
    }

    private static LocalDate future() {
        return LocalDate.now(MOSCOW).plusDays(7);
    }

    private static Map<String, Object> room(long id, long timesBooked) {
        return Map.of(
                "id",
                id,
                "hotelId",
                8L,
                "number",
                Long.toString(id),
                "available",
                true,
                "timesBooked",
                timesBooked);
    }

    private record Stay(
            UUID requestId,
            boolean autoSelect,
            Long roomId,
            LocalDate startDate,
            LocalDate endDate,
            Long hotelId) {
        static Stay manual(long roomId) {
            LocalDate start = future();
            return new Stay(UUID.randomUUID(), false, roomId, start, start.plusDays(2), null);
        }

        static Stay auto(Long roomId, Long hotelId) {
            LocalDate start = future();
            return new Stay(UUID.randomUUID(), true, roomId, start, start.plusDays(2), hotelId);
        }

        Stay withRoom(long newRoomId) {
            return new Stay(requestId, autoSelect, newRoomId, startDate, endDate, hotelId);
        }
    }
}
