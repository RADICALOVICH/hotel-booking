package ru.hotelbooking.hotel.availability;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ru.hotelbooking.hotel.availability.service.AvailabilityService;
import ru.hotelbooking.hotel.support.AdminTokenByDefault;
import ru.hotelbooking.hotel.support.CatalogFixture;
import ru.hotelbooking.hotel.support.HotelTestConfig;
import ru.hotelbooking.hotel.support.TestTokens;

import java.time.LocalDate;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({AdminTokenByDefault.class, CatalogFixture.class})
@ActiveProfiles("test")
class RoomSearchTest {
    private final MockMvc mvc;
    private final CatalogFixture catalog;
    private final AvailabilityService availability;
    private final String user;
    private long hotelId;
    private long first;
    private long second;
    private long third;

    @Autowired
    RoomSearchTest(
            MockMvc mvc,
            CatalogFixture catalog,
            AvailabilityService availability,
            TestTokens tokens) {
        this.mvc = mvc;
        this.catalog = catalog;
        this.availability = availability;
        this.user = "Bearer " + tokens.user();
    }

    @BeforeEach
    void createHotelWithThreeRooms() {
        hotelId = catalog.hotel();
        first = catalog.room(hotelId, "101");
        second = catalog.room(hotelId, "102");
        third = catalog.room(hotelId, "103");
    }

    @Test
    void neighbouringStaysShareTheCheckoutDay() throws Exception {
        hold(first, day(10), day(12));

        search("/api/rooms", day(12), day(14))
                .andExpect(jsonPath("$[*].id", contains((int) first, (int) second, (int) third)));
        search("/api/rooms", day(8), day(10))
                .andExpect(jsonPath("$[*].id", contains((int) first, (int) second, (int) third)));
    }

    @Test
    void overlappingAndNestedStaysHideTheRoom() throws Exception {
        hold(first, day(10), day(15));

        for (LocalDate[] stay :
                new LocalDate[][] {
                    {day(9), day(11)}, {day(14), day(16)}, {day(11), day(13)}, {day(8), day(20)}
                }) {
            search("/api/rooms", stay[0], stay[1])
                    .andExpect(header().string("X-Total-Count", "2"))
                    .andExpect(jsonPath("$[*].id", contains((int) second, (int) third)));
        }
    }

    @Test
    void roomOutOfServiceIsHiddenEvenWithoutStays() throws Exception {
        mvc.perform(
                        patch("/api/rooms/" + second)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"available\":false}"))
                .andExpect(status().isOk());

        search("/api/rooms", day(1), day(2))
                .andExpect(jsonPath("$[*].id", contains((int) first, (int) third)));
    }

    @Test
    void recommendationsListTheSameFreeRoomsLeastUsedFirst() throws Exception {
        hold(first, day(1), day(2));
        hold(first, day(3), day(4));
        hold(third, day(5), day(6));

        search("/api/rooms/recommend", day(20), day(22))
                .andExpect(jsonPath("$[*].id", contains((int) second, (int) third, (int) first)))
                .andExpect(jsonPath("$[*].timesBooked", contains(0, 1, 2)));
        search("/api/rooms", day(20), day(22))
                .andExpect(jsonPath("$[*].id", contains((int) first, (int) second, (int) third)));

        long fourth = catalog.room(hotelId, "104");
        search("/api/rooms/recommend", day(20), day(22))
                .andExpect(
                        jsonPath(
                                "$[*].id",
                                contains((int) second, (int) fourth, (int) third, (int) first)));
        search("/api/rooms/recommend", day(1), day(2))
                .andExpect(jsonPath("$[*].id", contains((int) second, (int) fourth, (int) third)));
    }

    @Test
    void searchValidatesDatesWithFieldErrors() throws Exception {
        mvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").isNotEmpty());
        search("/api/rooms", day(3), day(3))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.fieldErrors.endDate")
                                .value("Check-out must be after check-in"));
        search("/api/rooms/recommend", day(-1), day(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").isNotEmpty());
        search("/api/rooms", day(1), day(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").isNotEmpty());
        mvc.perform(
                        get("/api/rooms")
                                .header(HttpHeaders.AUTHORIZATION, user)
                                .param("startDate", "06.11.2026")
                                .param("endDate", day(2).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statisticsShowCountersAndCurrentStaysToAdminOnly() throws Exception {
        hold(first, day(1), day(2));
        hold(first, day(5), day(7));

        mvc.perform(get("/api/rooms/statistics").param("hotelId", "" + hotelId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$[0].roomId").value(first))
                .andExpect(jsonPath("$[0].timesBooked").value(2))
                .andExpect(jsonPath("$[0].activeAllocations").value(2))
                .andExpect(jsonPath("$[1].timesBooked").value(0));
        mvc.perform(get("/api/rooms/statistics").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isForbidden());
    }

    private ResultActions search(String path, LocalDate start, LocalDate end) throws Exception {
        return mvc.perform(
                get(path)
                        .header(HttpHeaders.AUTHORIZATION, user)
                        .param("hotelId", "" + hotelId)
                        .param("startDate", start.toString())
                        .param("endDate", end.toString()));
    }

    private void hold(long roomId, LocalDate start, LocalDate end) {
        availability.confirm(
                roomId, CatalogFixture.attempt(CatalogFixture.newAllocationId(), start, end));
    }

    private static LocalDate day(int offset) {
        return HotelTestConfig.TODAY.plusDays(offset);
    }
}
