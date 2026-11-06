package ru.hotelbooking.booking.booking.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.hotelbooking.booking.security.TestJwtKeys;
import ru.hotelbooking.booking.support.TestAccounts;
import ru.hotelbooking.booking.user.service.UserService;

@SpringBootTest(
        properties = {"DEMO_DATE=2026-11-06", "DEMO_USER_PASSWORD=" + TestAccounts.PASSWORD})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "demo"})
@Import(TestJwtKeys.class)
class DemoBookingTest {
    @Autowired private DemoBookings demo;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserService users;

    @Test
    void demoHistoryBelongsToTwoGuestsAndSeedingIsRepeatable() throws Exception {
        demo.afterSingletonsInstantiated();
        assertThat(jdbc.queryForObject("select count(*) from bookings", Integer.class))
                .isEqualTo(5);
        var accounts = new TestAccounts(mvc, json, users);
        var alice = accounts.login("demo-alice", TestAccounts.PASSWORD);
        var bob = accounts.login("demo-bob", TestAccounts.PASSWORD);
        mvc.perform(get("/booking/910005").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.roomId").value(910002));
        mvc.perform(get("/booking/910005").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiDescribesBookingResponsesAndExcludesInternalAccessCheck() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Booking Service"))
                .andExpect(
                        jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/booking'].post.security[0].bearerAuth").isArray())
                .andExpect(
                        jsonPath("$.paths['/booking'].post['x-authorization']")
                                .value("hasAnyRole('USER', 'ADMIN')"))
                .andExpect(jsonPath("$.paths['/booking'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/booking'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/booking'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/booking'].post.responses['504']").exists())
                .andExpect(jsonPath("$.paths['/user/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/user/auth'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/internal/auth/validate']").doesNotExist());
    }
}
