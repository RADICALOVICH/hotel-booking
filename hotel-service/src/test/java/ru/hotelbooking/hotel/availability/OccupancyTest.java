package ru.hotelbooking.hotel.availability;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.hotelbooking.hotel.support.AdminTokenByDefault;
import ru.hotelbooking.hotel.support.CatalogFixture;
import ru.hotelbooking.hotel.support.HotelTestConfig;
import ru.hotelbooking.hotel.support.TestTokens;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({AdminTokenByDefault.class, CatalogFixture.class})
@ActiveProfiles("test")
class OccupancyTest {
    private static final String HOTELS = "/api/hotels/statistics";
    private static final String ROOMS = "/api/rooms/statistics";
    private static final LocalDate END = HotelTestConfig.TODAY;
    private static final LocalDate START = END.minusDays(5);
    private static final OffsetDateTime CREATED =
            START.minusDays(8).atStartOfDay().atOffset(ZoneOffset.UTC);

    private final MockMvc mvc;
    private final CatalogFixture catalog;
    private final JdbcTemplate jdbc;
    private final TestTokens tokens;
    private long hotel;
    private long busy;
    private long idle;
    private long closed;

    @Autowired
    OccupancyTest(MockMvc mvc, CatalogFixture catalog, JdbcTemplate jdbc, TestTokens tokens) {
        this.mvc = mvc;
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    @BeforeEach
    void arrangeHistoricalStays() throws Exception {
        hotel = catalog.hotel();
        busy = catalog.room(hotel, "101");
        idle = catalog.room(hotel, "102");
        closed = catalog.room(hotel, "103");

        insert(busy, START.minusDays(2), START.plusDays(2), "ACTIVE");
        insert(busy, END.minusDays(1), END.plusDays(2), "ACTIVE");
        insert(busy, START.minusDays(5), START.minusDays(3), "ACTIVE");
        insert(busy, START.plusDays(2), START.plusDays(3), "RELEASED");
        insert(busy, START.plusDays(3), START.plusDays(4), "REJECTED");
        insert(idle, START.minusDays(2), START, "ACTIVE");
        insert(idle, END, END.plusDays(2), "ACTIVE");
        insert(closed, START.plusDays(1), END, "ACTIVE");
        mvc.perform(
                        patch("/api/rooms/{id}", closed)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"available\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void countsOnlyOverlappingActiveNightsAndKeepsTheLegacyRoomSnapshot() throws Exception {
        mvc.perform(report(ROOMS, hotel))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$[0].roomId").value(busy))
                .andExpect(jsonPath("$[0].timesBooked").value(3))
                .andExpect(jsonPath("$[0].allocationCount").value(2))
                .andExpect(jsonPath("$[0].occupiedRoomNights").value(3))
                .andExpect(jsonPath("$[0].occupiedAvailableRoomNights").value(3))
                .andExpect(jsonPath("$[0].capacityRoomNights").value(5))
                .andExpect(jsonPath("$[0].occupancyPercent").value(60.0))
                .andExpect(jsonPath("$[1].roomId").value(idle))
                .andExpect(jsonPath("$[1].allocationCount").value(0))
                .andExpect(jsonPath("$[1].occupiedRoomNights").value(0))
                .andExpect(jsonPath("$[2].roomId").value(closed))
                .andExpect(jsonPath("$[2].allocationCount").value(1))
                .andExpect(jsonPath("$[2].occupiedRoomNights").value(4))
                .andExpect(jsonPath("$[2].occupiedAvailableRoomNights").value(0))
                .andExpect(jsonPath("$[2].capacityRoomNights").value(0))
                .andExpect(jsonPath("$[2].occupancyPercent").value(nullValue()));

        mvc.perform(get(ROOMS).param("hotelId", Long.toString(hotel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activeAllocations").value(1))
                .andExpect(jsonPath("$[0].occupiedRoomNights").doesNotExist());
    }

    @Test
    void calculatesHotelCapacityUsingCurrentOpenRoomsButRetainsClosedHistory() throws Exception {
        mvc.perform(report(HOTELS, hotel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hotelId").value(hotel))
                .andExpect(jsonPath("$[0].roomCount").value(3))
                .andExpect(jsonPath("$[0].availableRoomCount").value(2))
                .andExpect(jsonPath("$[0].allocationCount").value(3))
                .andExpect(jsonPath("$[0].occupiedRoomNights").value(7))
                .andExpect(jsonPath("$[0].occupiedAvailableRoomNights").value(3))
                .andExpect(jsonPath("$[0].capacityRoomNights").value(10))
                .andExpect(jsonPath("$[0].occupancyPercent").value(30.0))
                .andExpect(jsonPath("$[0].timesBookedSpread").value(1));

        mvc.perform(report(HOTELS, hotel).param("available", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomCount").value(1))
                .andExpect(jsonPath("$[0].availableRoomCount").value(0))
                .andExpect(jsonPath("$[0].occupiedRoomNights").value(4))
                .andExpect(jsonPath("$[0].occupancyPercent").value(nullValue()));

        mvc.perform(report(HOTELS, catalog.hotel()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].roomCount").value(0))
                .andExpect(jsonPath("$[0].occupiedRoomNights").value(0))
                .andExpect(jsonPath("$[0].occupancyPercent").value(nullValue()));
    }

    @Test
    void filtersAndSortsBeforePaginatingAndLeavesUnknownPercentLast() throws Exception {
        mvc.perform(
                        report(ROOMS, hotel)
                                .param("sort", "occupiedRoomNights,desc")
                                .param("page", "1")
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$[*].roomId", contains((int) busy)));

        mvc.perform(
                        report(ROOMS, hotel)
                                .param("available", "true")
                                .param("sort", "allocationCount"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[*].roomId", contains((int) idle, (int) busy)));

        mvc.perform(report(ROOMS, hotel).param("sort", "occupancyPercent,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].roomId", contains((int) busy, (int) idle, (int) closed)));
    }

    @Test
    void validatesReportDatesAndSortKeys() throws Exception {
        mvc.perform(get(HOTELS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").isNotEmpty());
        mvc.perform(get(ROOMS).param("startDate", START.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").isNotEmpty());
        mvc.perform(get(HOTELS).param("startDate", END.toString()).param("endDate", END.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").isNotEmpty());
        mvc.perform(report(HOTELS, hotel).param("sort", "timesBooked"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sort").isNotEmpty());
        mvc.perform(report(ROOMS, hotel).param("sort", "id,down"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sort").isNotEmpty());
    }

    @Test
    void requiresAnAdministratorForBothReports() throws Exception {
        for (String path : new String[] {HOTELS, ROOMS}) {
            mvc.perform(
                            report(path, hotel)
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.user()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mvc.perform(
                            report(path, hotel)
                                    .header(
                                            HttpHeaders.AUTHORIZATION,
                                            "Bearer " + tokens.service()))
                    .andExpect(status().isForbidden());
        }
    }

    private MockHttpServletRequestBuilder report(String path, long hotelId) {
        return get(path)
                .param("hotelId", Long.toString(hotelId))
                .param("startDate", START.toString())
                .param("endDate", END.toString());
    }

    private void insert(long room, LocalDate start, LocalDate end, String state) {
        jdbc.update(
                """
                insert into room_allocations
                  (allocation_id, booking_id, room_id, start_date, end_date, state, created_at, released_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                CatalogFixture.newAllocationId(),
                room,
                room,
                start,
                end,
                state,
                CREATED,
                state.equals("RELEASED") ? CREATED.plusDays(1) : null);
        if (state.equals("ACTIVE")) {
            jdbc.update("update rooms set times_booked = times_booked + 1 where id = ?", room);
        }
    }
}
