package ru.hotelbooking.hotel.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.hotelbooking.hotel.availability.service.AvailabilityService;
import ru.hotelbooking.hotel.support.AdminTokenByDefault;
import ru.hotelbooking.hotel.support.CatalogFixture;
import ru.hotelbooking.hotel.support.HotelTestConfig;
import ru.hotelbooking.hotel.support.StubBookingOccupancy;

import java.time.LocalDate;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({AdminTokenByDefault.class, CatalogFixture.class})
@ActiveProfiles("test")
class CanonicalOccupancyTest {
    private static final LocalDate START = HotelTestConfig.TODAY.plusDays(10);
    private static final LocalDate END = START.plusDays(2);
    @Autowired private MockMvc mvc;
    @Autowired private CatalogFixture catalog;
    @Autowired private AvailabilityService availability;
    @Autowired private StubBookingOccupancy bookings;

    @Test
    void bookingWithoutLocalAllocationBlocksSearchConfirmAndCatalogChanges() throws Exception {
        long hotel = catalog.hotel();
        long room = catalog.room(hotel, "canonical");
        bookings.add(room, START, END);
        mvc.perform(
                        get("/api/rooms")
                                .param("hotelId", "" + hotel)
                                .param("startDate", START.toString())
                                .param("endDate", END.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"));
        assertThat(
                        availability
                                .confirm(
                                        room,
                                        CatalogFixture.attempt(
                                                CatalogFixture.newAllocationId(), START, END))
                                .state()
                                .name())
                .isEqualTo("REJECTED");
        mvc.perform(
                        patch("/api/rooms/{id}", room)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"available\":false}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/rooms/{id}", room)).andExpect(status().isConflict());
    }

    @Test
    void reportUsesBookingDatesEvenWhenNoAllocationExists() throws Exception {
        long hotel = catalog.hotel();
        long room = catalog.room(hotel, "bookings-report");
        bookings.add(room, START.minusDays(2), END);
        mvc.perform(
                        get("/api/rooms/statistics")
                                .param("hotelId", "" + hotel)
                                .param("startDate", START.toString())
                                .param("endDate", END.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].allocationCount").value(1))
                .andExpect(jsonPath("$[0].occupiedRoomNights").value(2))
                .andExpect(jsonPath("$[0].occupancyPercent").value(100.0));
    }

    @Test
    void localHoldProtectsAnInFlightBookingButDoesNotBecomeConfirmedReportData() throws Exception {
        long hotel = catalog.hotel();
        long room = catalog.room(hotel, "pending-hold");
        var attempt = CatalogFixture.attempt(CatalogFixture.newAllocationId(), START, END);
        availability.confirm(room, attempt);
        mvc.perform(
                        get("/api/rooms")
                                .param("hotelId", "" + hotel)
                                .param("startDate", START.toString())
                                .param("endDate", END.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"));
        mvc.perform(
                        get("/api/rooms/statistics")
                                .param("hotelId", "" + hotel)
                                .param("startDate", START.toString())
                                .param("endDate", END.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].allocationCount").value(0))
                .andExpect(jsonPath("$[0].occupiedRoomNights").value(0));
        availability.release(room, attempt);
        mvc.perform(
                        get("/api/rooms")
                                .param("hotelId", "" + hotel)
                                .param("startDate", START.toString())
                                .param("endDate", END.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    void occupancyOutageFailsClosedButReleaseAndKnownReplayRemainAvailable() throws Exception {
        long hotel = catalog.hotel();
        long room = catalog.room(hotel, "outage");
        var attempt = CatalogFixture.attempt(CatalogFixture.newAllocationId(), START, END);
        availability.confirm(room, attempt);
        bookings.unavailable(true);
        try {
            mvc.perform(
                            get("/api/rooms")
                                    .param("hotelId", "" + hotel)
                                    .param("startDate", START.toString())
                                    .param("endDate", END.toString()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("BOOKING_OCCUPANCY_UNAVAILABLE"));
            assertThat(availability.confirm(room, attempt).state().name()).isEqualTo("ACTIVE");
            assertThat(availability.release(room, attempt).state().name()).isEqualTo("RELEASED");
        } finally {
            bookings.unavailable(false);
        }
    }
}
