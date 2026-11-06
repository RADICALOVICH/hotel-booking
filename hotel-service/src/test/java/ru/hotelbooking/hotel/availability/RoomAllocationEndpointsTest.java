package ru.hotelbooking.hotel.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ru.hotelbooking.hotel.availability.dto.AllocationRequest;
import ru.hotelbooking.hotel.support.AdminTokenByDefault;
import ru.hotelbooking.hotel.support.CatalogFixture;
import ru.hotelbooking.hotel.support.HotelTestConfig;
import ru.hotelbooking.hotel.support.TestTokens;

import java.time.LocalDate;
import java.util.Map;

/** HTTP contract of the saga steps, called with Booking's service token. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({AdminTokenByDefault.class, CatalogFixture.class})
@ActiveProfiles("test")
class RoomAllocationEndpointsTest {
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final CatalogFixture catalog;
    private final String service;
    private long roomId;

    @Autowired
    RoomAllocationEndpointsTest(
            MockMvc mvc,
            ObjectMapper json,
            JdbcTemplate jdbc,
            CatalogFixture catalog,
            TestTokens tokens) {
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.service = "Bearer " + tokens.service();
    }

    @BeforeEach
    void createRoom() {
        roomId = catalog.room(catalog.hotel(), "201");
    }

    @Test
    void confirmHoldsDatesOnceAndRepeatsAreIdempotent() throws Exception {
        AllocationRequest attempt = attempt(day(10), day(12));

        for (int delivery = 0; delivery < 3; delivery++) {
            confirm(attempt)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("ACTIVE"))
                    .andExpect(jsonPath("$.allocationId").value(attempt.allocationId()))
                    .andExpect(jsonPath("$.roomId").value(roomId));
        }

        assertThat(catalog.reload(roomId).getTimesBooked()).isEqualTo(1);
        assertThat(rows(attempt.allocationId())).isEqualTo(1);
        confirm(
                        new AllocationRequest(
                                attempt.bookingId(), attempt.allocationId(), day(10), day(13)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_MISMATCH"));
    }

    @Test
    void overlappingAttemptIsRejectedAndStaysRejected() throws Exception {
        confirm(attempt(day(10), day(15))).andExpect(status().isOk());
        AllocationRequest late = attempt(day(14), day(16));

        confirm(late)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));
        confirm(late).andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));

        assertThat(state(late.allocationId())).isEqualTo("REJECTED");
        assertThat(catalog.reload(roomId).getTimesBooked()).isEqualTo(1);
    }

    @Test
    void releaseFreesTheDatesOnceAndRepeatsAreSafe() throws Exception {
        AllocationRequest attempt = attempt(day(10), day(12));
        confirm(attempt).andExpect(status().isOk());

        for (int delivery = 0; delivery < 3; delivery++) {
            release(attempt)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("RELEASED"));
        }

        assertThat(catalog.reload(roomId).getTimesBooked()).isZero();
        confirm(attempt(day(10), day(12))).andExpect(status().isOk());
        assertThat(catalog.reload(roomId).getTimesBooked()).isEqualTo(1);
    }

    @Test
    void releaseBeforeConfirmBlocksTheLateConfirm() throws Exception {
        AllocationRequest attempt = attempt(day(10), day(12));

        release(attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RELEASED"));
        confirm(attempt)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_RELEASED"));

        assertThat(state(attempt.allocationId())).isEqualTo("RELEASED");
        assertThat(catalog.reload(roomId).getTimesBooked()).isZero();
    }

    @Test
    void releaseWithForeignBookingDoesNotTouchTheAllocation() throws Exception {
        AllocationRequest attempt = attempt(day(10), day(12));
        confirm(attempt).andExpect(status().isOk());

        release(new AllocationRequest(999L, attempt.allocationId(), day(10), day(12)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_MISMATCH"));

        assertThat(state(attempt.allocationId())).isEqualTo("ACTIVE");
        assertThat(catalog.reload(roomId).getTimesBooked()).isEqualTo(1);
    }

    @Test
    void validatesBodyDatesAndRoom() throws Exception {
        confirm(attempt(day(-1), day(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").isNotEmpty());
        send("/confirm-availability", roomId, Map.of("allocationId", "x", "startDate", day(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.bookingId").isNotEmpty());
        send("/confirm-availability", Long.MAX_VALUE, body(attempt(day(1), day(2))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        release(attempt(day(-5), day(-3))).andExpect(status().isOk());
    }

    @Test
    void roomWithStaysCannotBeDisabledAndRoomWithHistoryCannotBeDeleted() throws Exception {
        AllocationRequest attempt = attempt(day(10), day(12));
        confirm(attempt).andExpect(status().isOk());

        mvc.perform(
                        patch("/api/rooms/" + roomId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"available\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_HAS_ACTIVE_ALLOCATIONS"));

        release(attempt).andExpect(status().isOk());
        mvc.perform(
                        patch("/api/rooms/" + roomId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"available\":false}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/rooms/" + roomId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_HAS_ALLOCATION_HISTORY"));
        confirm(attempt(day(20), day(21)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));
    }

    private ResultActions confirm(AllocationRequest request) throws Exception {
        return send("/confirm-availability", roomId, body(request));
    }

    private ResultActions release(AllocationRequest request) throws Exception {
        return send("/release", roomId, body(request));
    }

    private ResultActions send(String action, long room, Map<String, Object> body)
            throws Exception {
        return mvc.perform(
                post("/api/rooms/" + room + action)
                        .header(HttpHeaders.AUTHORIZATION, service)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private static Map<String, Object> body(AllocationRequest request) {
        return Map.of(
                "bookingId", request.bookingId(),
                "allocationId", request.allocationId(),
                "startDate", request.startDate(),
                "endDate", request.endDate());
    }

    private static AllocationRequest attempt(LocalDate start, LocalDate end) {
        return CatalogFixture.attempt(CatalogFixture.newAllocationId(), start, end);
    }

    private String state(String allocationId) {
        return jdbc.queryForObject(
                "select state from room_allocations where allocation_id = ?",
                String.class,
                allocationId);
    }

    private int rows(String allocationId) {
        return jdbc.queryForObject(
                "select count(*) from room_allocations where allocation_id = ?",
                Integer.class,
                allocationId);
    }

    private static LocalDate day(int offset) {
        return HotelTestConfig.TODAY.plusDays(offset);
    }
}
