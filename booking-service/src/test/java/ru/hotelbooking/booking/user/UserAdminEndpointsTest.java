package ru.hotelbooking.booking.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.hotelbooking.booking.security.TestJwtKeys;
import ru.hotelbooking.booking.support.TestAccounts;
import ru.hotelbooking.booking.support.TestAccounts.Account;
import ru.hotelbooking.booking.user.controller.UserAdminController;
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
class UserAdminEndpointsTest {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final String NEW_PASSWORD = "another-password-77";
    private static final String VALIDATE = "/internal/auth/validate";

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final UserAdminController controller;
    private final TestAccounts accounts;
    private Account admin;

    @Autowired
    UserAdminEndpointsTest(
            MockMvc mvc,
            ObjectMapper json,
            JdbcTemplate jdbc,
            UserService users,
            UserAdminController controller) {
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
        this.controller = controller;
        this.accounts = new TestAccounts(mvc, json, users);
    }

    @BeforeEach
    void signInAdmin() throws Exception {
        admin = accounts.admin(uniqueName("admin"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCreatesReadsRenamesAndDeletesUser() throws Exception {
        long id =
                json.readTree(
                                asAdmin(
                                                post("/user"),
                                                Map.of(
                                                        "username", " Managed-Guest ",
                                                        "password", TestAccounts.PASSWORD,
                                                        "role", "user"))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.username").value("managed-guest"))
                                        .andExpect(jsonPath("$.role").value("USER"))
                                        .andExpect(jsonPath("$.password").doesNotExist())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsByteArray())
                        .path("id")
                        .asLong();

        asAdmin(get("/user/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("managed-guest"));
        asAdmin(get("/users").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(jsonPath("$[?(@.id == %d)].username", id).value("managed-guest"));
        asAdmin(patch("/user/" + id), Map.of("username", "Renamed-Guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("renamed-guest"));
        accounts.login("renamed-guest", TestAccounts.PASSWORD);

        asAdmin(delete("/user/" + id)).andExpect(status().isNoContent());
        asAdmin(get("/user/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        asAdmin(delete("/user/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void roleOrPasswordChangeRevokesTokensWhileRenameKeepsThem() throws Exception {
        Account guest = accounts.user(uniqueName("guest"));

        asAdmin(patch("/user/" + guest.id()), Map.of("username", uniqueName("renamed")))
                .andExpect(status().isOk());
        validate(guest).andExpect(status().isNoContent());

        String login = currentUsername(guest.id());
        asAdmin(patch("/user/" + guest.id()), Map.of("password", NEW_PASSWORD))
                .andExpect(status().isOk());
        validate(guest).andExpect(status().isUnauthorized());
        Account renewed = accounts.login(login, NEW_PASSWORD);
        validate(renewed).andExpect(status().isNoContent());

        asAdmin(patch("/user/" + guest.id()), Map.of("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        validate(renewed).andExpect(status().isUnauthorized());
        Account promoted = accounts.login(login, NEW_PASSWORD);
        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, promoted.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void deletedUserTokenIsRejectedImmediately() throws Exception {
        Account guest = accounts.user(uniqueName("leaving"));
        validate(guest).andExpect(status().isNoContent());

        asAdmin(delete("/user/" + guest.id())).andExpect(status().isNoContent());

        mvc.perform(get("/bookings").header(HttpHeaders.AUTHORIZATION, guest.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesToDeleteUserWithAnyBookingRecord() throws Exception {
        Account guest = accounts.user(uniqueName("booked"));
        jdbc.update(
                "insert into bookings (user_id, room_id, start_date, end_date, status, created_at)"
                        + " values (?, ?, ?, ?, ?, ?)",
                guest.id(),
                7L,
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 12),
                "CANCELLED",
                OffsetDateTime.parse("2026-01-01T10:00:00Z"));

        asAdmin(delete("/user/" + guest.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_HAS_BOOKINGS"));
        asAdmin(get("/user/" + guest.id())).andExpect(status().isOk());
    }

    @Test
    void onlyAdminsReachUserManagement() throws Exception {
        Account guest = accounts.user(uniqueName("plain"));
        List<MockHttpServletRequestBuilder> requests =
                List.of(
                        post("/user").contentType(MediaType.APPLICATION_JSON).content("{}"),
                        get("/users"),
                        get("/user/" + guest.id()),
                        patch("/user/" + guest.id())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"ADMIN\"}"),
                        delete("/user/" + guest.id()));

        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, guest.bearer()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        validate(guest).andExpect(status().isNoContent());
    }

    @Test
    void preAuthorizeProtectsControllerEvenWithoutHttpRules() {
        authenticateAs("ROLE_USER");
        assertThatThrownBy(() -> controller.list(0, 20)).isInstanceOf(AccessDeniedException.class);

        authenticateAs("ROLE_ADMIN");
        assertThat(controller.list(0, 20).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void validatesAdminInputWithUnifiedErrors() throws Exception {
        String taken = uniqueName("taken");
        accounts.user(taken);
        Account guest = accounts.user(uniqueName("target"));

        createUser(Map.of("username", uniqueName("x"), "password", TestAccounts.PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role").isNotEmpty());
        createUser(
                        Map.of(
                                "username",
                                uniqueName("x"),
                                "password",
                                TestAccounts.PASSWORD,
                                "role",
                                "OWNER"))
                .andExpect(jsonPath("$.fieldErrors.role").value("Role must be USER or ADMIN"));
        createUser(Map.of("username", uniqueName("x"), "password", "short", "role", "USER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
        createUser(Map.of("username", taken, "password", TestAccounts.PASSWORD, "role", "USER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));

        String target = "/user/" + guest.id();
        asAdmin(patch(target), Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        asAdminRaw(patch(target), "{\"role\":null}").andExpect(status().isBadRequest());
        asAdmin(patch(target), Map.of("email", "x@example.test"))
                .andExpect(status().isBadRequest());
        asAdmin(patch(target), Map.of("username", taken.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
        asAdmin(patch("/user/999999"), Map.of("role", "USER")).andExpect(status().isNotFound());
        asAdmin(get("/users").param("size", "101")).andExpect(status().isBadRequest());
        asAdmin(get("/user/not-a-number")).andExpect(status().isBadRequest());
    }

    private ResultActions createUser(Map<String, Object> body) throws Exception {
        return asAdmin(post("/user"), body);
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, admin.bearer()));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request, Map<String, ?> body)
            throws Exception {
        return asAdminRaw(request, json.writeValueAsString(body));
    }

    private ResultActions asAdminRaw(MockHttpServletRequestBuilder request, String body)
            throws Exception {
        return asAdmin(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions validate(Account account) throws Exception {
        return mvc.perform(get(VALIDATE).header(HttpHeaders.AUTHORIZATION, account.bearer()));
    }

    private String currentUsername(long id) {
        return jdbc.queryForObject("select username from users where id = ?", String.class, id);
    }

    private static void authenticateAs(String authority) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "tester", null, AuthorityUtils.createAuthorityList(authority)));
    }

    private static String uniqueName(String prefix) {
        return "admin-test-" + prefix + "-" + NAMES.incrementAndGet();
    }
}
