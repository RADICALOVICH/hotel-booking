package ru.hotelbooking.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.hotelbooking.booking.security.TestJwtKeys;
import ru.hotelbooking.booking.support.TestAccounts;
import ru.hotelbooking.booking.support.TestAccounts.Account;
import ru.hotelbooking.booking.user.service.UserService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(TestJwtKeys.class)
@ActiveProfiles("test")
class BookingHistoryTest {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final OffsetDateTime EARLY = OffsetDateTime.parse("2026-10-01T09:00:00Z");
    private static final OffsetDateTime LATE = OffsetDateTime.parse("2026-10-02T09:00:00Z");

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final TestAccounts accounts;
    private Account alice;
    private Account bob;

    @Autowired
    BookingHistoryTest(MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc, UserService users) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.accounts = new TestAccounts(mvc, json, users);
    }

    @BeforeEach
    void createGuests() throws Exception {
        alice = accounts.user(uniqueName("alice"));
        bob = accounts.user(uniqueName("bob"));
    }

    @Test
    void historyShowsOnlyOwnBookingsNewestFirstWithStableTies() throws Exception {
        long oldest = insertBooking(alice, "CONFIRMED", EARLY);
        long tieLow = insertBooking(alice, "CANCELLED", LATE);
        long tieHigh = insertBooking(alice, "PENDING", LATE);
        insertBooking(bob, "CONFIRMED", LATE);

        perform(alice, get("/bookings").param("size", "2").param("userId", "" + bob.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$[*].id", contains((int) tieHigh, (int) tieLow)));
        perform(alice, get("/bookings").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains((int) oldest)));
    }

    @Test
    void bookingCardIsVisibleOnlyToItsOwner() throws Exception {
        long own = insertBooking(alice, "CONFIRMED", EARLY);
        long foreign = insertBooking(bob, "CONFIRMED", EARLY);

        perform(alice, get("/booking/" + own))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(own))
                .andExpect(jsonPath("$.roomId").value(7))
                .andExpect(jsonPath("$.startDate").value("2026-12-10"))
                .andExpect(jsonPath("$.endDate").value("2026-12-12"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.userId").doesNotExist());
        for (long hidden : List.of(foreign, 999_999L)) {
            perform(alice, get("/booking/" + hidden))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        }
    }

    @Test
    void adminUsesPersonalEndpointsOnlyForOwnBookings() throws Exception {
        Account admin = accounts.admin(uniqueName("admin"));
        long aliceBooking = insertBooking(alice, "CONFIRMED", EARLY);
        long adminBooking = insertBooking(admin, "PENDING", LATE);

        perform(admin, get("/bookings"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[*].id", contains((int) adminBooking)));
        perform(admin, get("/booking/" + aliceBooking)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsMissingTokenAndInvalidPaging() throws Exception {
        mvc.perform(get("/bookings")).andExpect(status().isUnauthorized());
        for (Map.Entry<String, String> invalid :
                List.of(
                        Map.entry("size", "0"),
                        Map.entry("size", "101"),
                        Map.entry("page", "-1"))) {
            perform(alice, get("/bookings").param(invalid.getKey(), invalid.getValue()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void schemaKeepsRequiredColumnsAndRejectsInvalidRows() {
        List<String> columns =
                jdbc.queryForList(
                        "select lower(column_name) from information_schema.columns"
                                + " where lower(table_name) = 'bookings'",
                        String.class);
        assertThat(columns)
                .contains(
                        "id",
                        "user_id",
                        "room_id",
                        "start_date",
                        "end_date",
                        "status",
                        "created_at");

        assertThatThrownBy(
                        () ->
                                insert(
                                        alice.id(),
                                        LocalDate.of(2026, 12, 12),
                                        LocalDate.of(2026, 12, 12)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                insert(
                                        999_999L,
                                        LocalDate.of(2026, 12, 10),
                                        LocalDate.of(2026, 12, 12)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ResultActions perform(Account account, MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, account.bearer()));
    }

    private long insertBooking(Account owner, String status, OffsetDateTime createdAt) {
        return new SimpleJdbcInsert(jdbc)
                .withTableName("bookings")
                .usingColumns(
                        "user_id", "room_id", "start_date", "end_date", "status", "created_at")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(
                        Map.of(
                                "user_id",
                                owner.id(),
                                "room_id",
                                7L,
                                "start_date",
                                LocalDate.of(2026, 12, 10),
                                "end_date",
                                LocalDate.of(2026, 12, 12),
                                "status",
                                status,
                                "created_at",
                                createdAt))
                .longValue();
    }

    private void insert(long userId, LocalDate start, LocalDate end) {
        jdbc.update(
                "insert into bookings (user_id, start_date, end_date, status, created_at)"
                        + " values (?, ?, ?, 'PENDING', ?)",
                userId,
                start,
                end,
                EARLY);
    }

    private static String uniqueName(String prefix) {
        return "history-" + prefix + "-" + NAMES.incrementAndGet();
    }
}
