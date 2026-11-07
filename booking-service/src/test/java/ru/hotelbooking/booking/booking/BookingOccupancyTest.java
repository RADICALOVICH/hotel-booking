package ru.hotelbooking.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.hotelbooking.booking.security.TestJwtKeys;
import ru.hotelbooking.booking.support.TestAccounts;
import ru.hotelbooking.booking.user.service.UserService;
import ru.hotelbooking.support.security.OccupancyJwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(TestJwtKeys.class)
@ActiveProfiles("test")
class BookingOccupancyTest {
    private static final String SECRET = "test-only-occupancy-key-at-least-32-bytes";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private UserService users;

    @Test
    void readsConfirmedBookingsAcrossOwnersWithoutLeakingIdentityAndUsesHalfOpenDates()
            throws Exception {
        var accounts = new TestAccounts(mvc, json, users);
        var first = accounts.user("occupancy-" + UUID.randomUUID());
        var second = accounts.user("occupancy-" + UUID.randomUUID());
        seed(first.id(), 780001, "CONFIRMED", "2026-12-10", "2026-12-12");
        seed(second.id(), 780002, "CONFIRMED", "2026-12-11", "2026-12-13");
        seed(first.id(), 780003, "CANCELLED", "2026-12-10", "2026-12-12");
        seed(first.id(), 780004, "PENDING", "2026-12-10", "2026-12-12");
        seed(first.id(), 780005, "CONFIRMED", "2026-12-08", "2026-12-10");
        var response =
                mvc.perform(
                                get(OccupancyJwt.PATH)
                                        .param("startDate", "2026-12-10")
                                        .param("endDate", "2026-12-12")
                                        .header(
                                                HttpHeaders.AUTHORIZATION,
                                                bearer(Clock.systemUTC())))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        var body = json.readTree(response.getContentAsByteArray());
        assertThat(body.findValuesAsText("roomId"))
                .contains("780001", "780002")
                .doesNotContain("780003", "780004", "780005");
        assertThat(body.findValues("userId")).isEmpty();
        assertThat(body.findValues("bookingId")).isEmpty();
        assertThat(body.findValues("username")).isEmpty();
        assertThat(body.findValues("requestId")).isEmpty();
    }

    @Test
    void userTokenCannotReadInternalOccupancyAndOccupancyTokenCannotUseUserApis() throws Exception {
        var user = new TestAccounts(mvc, json, users).user("occupancy-rights-" + UUID.randomUUID());
        mvc.perform(
                        get(OccupancyJwt.PATH)
                                .param("startDate", "2026-12-10")
                                .param("endDate", "2026-12-12")
                                .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/bookings").header(HttpHeaders.AUTHORIZATION, bearer(Clock.systemUTC())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(OccupancyJwt.PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidDatesExpiredTokensAndAnotherSecret() throws Exception {
        mvc.perform(
                        get(OccupancyJwt.PATH)
                                .param("startDate", "2026-12-12")
                                .param("endDate", "2026-12-10")
                                .header(HttpHeaders.AUTHORIZATION, bearer(Clock.systemUTC())))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get(OccupancyJwt.PATH)
                                .param("startDate", "2026-12-10")
                                .param("endDate", "2026-12-12")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                Clock.offset(
                                                        Clock.systemUTC(),
                                                        Duration.ofMinutes(-5)))))
                .andExpect(status().isUnauthorized());
        String foreign =
                OccupancyJwt.issue(OccupancyJwt.encoder(SECRET + "-foreign"), Clock.systemUTC());
        mvc.perform(
                        get(OccupancyJwt.PATH)
                                .param("startDate", "2026-12-10")
                                .param("endDate", "2026-12-12")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({
        "http://another,booking-occupancy,hotel-service",
        "http://hotel-service,hotel-internal,hotel-service",
        "http://hotel-service,booking-occupancy,another-service"
    })
    void rejectsAnotherIssuerAudienceOrSubjectEvenWithTheCorrectSigningKey(
            String issuer, String audience, String subject) throws Exception {
        var now = Instant.now();
        var claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .subject(subject)
                        .audience(List.of(audience))
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(60))
                        .build();
        String token =
                OccupancyJwt.encoder(SECRET)
                        .encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue();
        mvc.perform(
                        get(OccupancyJwt.PATH)
                                .param("startDate", "2026-12-10")
                                .param("endDate", "2026-12-12")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(Clock clock) {
        return "Bearer " + OccupancyJwt.issue(OccupancyJwt.encoder(SECRET), clock);
    }

    private void seed(long user, long room, String status, String start, String end) {
        jdbc.update(
                """
                insert into bookings (user_id, room_id, start_date, end_date, status, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                user,
                room,
                LocalDate.parse(start),
                LocalDate.parse(end),
                status,
                Instant.now());
    }
}
