package ru.hotelbooking.booking.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.hotelbooking.booking.security.TestJwtKeys;
import ru.hotelbooking.booking.support.TestAccounts;
import ru.hotelbooking.booking.support.TestAccounts.Account;
import ru.hotelbooking.booking.user.config.InitialAdminInitializer;
import ru.hotelbooking.booking.user.dto.PatchUserRequest;
import ru.hotelbooking.booking.user.entity.UserRole;
import ru.hotelbooking.booking.user.exception.LastAdminException;
import ru.hotelbooking.booking.user.service.UserAdminService;
import ru.hotelbooking.booking.user.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Every test starts a fresh context, so the configured root admin is the only ADMIN. */
@SpringBootTest(
        properties = {
            "booking.initial-admin.username=Root-Admin",
            "booking.initial-admin.password=" + TestAccounts.PASSWORD
        })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(TestJwtKeys.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InitialAdminTest {
    private static final String ROOT = "root-admin";

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final InitialAdminInitializer initializer;
    private final UserService users;
    private final UserAdminService admin;
    private final TestAccounts accounts;

    @Autowired
    InitialAdminTest(
            MockMvc mvc,
            ObjectMapper json,
            JdbcTemplate jdbc,
            InitialAdminInitializer initializer,
            UserService users,
            UserAdminService admin) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.initializer = initializer;
        this.users = users;
        this.admin = admin;
        this.accounts = new TestAccounts(mvc, json, users);
    }

    @Test
    void createsConfiguredAdminOnceOnStartup() throws Exception {
        Account root = accounts.login("ROOT-ADMIN", TestAccounts.PASSWORD);

        initializer.afterSingletonsInstantiated();

        assertThat(adminCount()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from users where username = ?",
                                Integer.class,
                                ROOT))
                .isEqualTo(1);
        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, root.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void keepsAtLeastOneAdmin() throws Exception {
        Account root = accounts.login(ROOT, TestAccounts.PASSWORD);

        send(root, patch("/user/" + root.id()), "{\"role\":\"USER\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        send(root, delete("/user/" + root.id()), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));

        Account second = accounts.admin("second-admin");
        send(root, patch("/user/" + root.id()), "{\"role\":\"USER\"}").andExpect(status().isOk());
        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, root.bearer()))
                .andExpect(status().isUnauthorized());
        send(second, delete("/user/" + second.id()), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        assertThat(adminCount()).isEqualTo(1);
    }

    @Test
    void concurrentDemotionsLeaveOneAdmin() throws Exception {
        long rootId = accounts.login(ROOT, TestAccounts.PASSWORD).id();
        long secondId =
                users.createAccount("second-admin", TestAccounts.PASSWORD, UserRole.ADMIN).getId();
        PatchUserRequest demote = new PatchUserRequest(null, null, "USER");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<CompletableFuture<String>> results = new ArrayList<>();

        try {
            for (long id : new long[] {rootId, secondId}) {
                results.add(
                        CompletableFuture.supplyAsync(
                                () -> {
                                    await(start);
                                    try {
                                        admin.update(id, demote);
                                        return "demoted";
                                    } catch (LastAdminException exception) {
                                        return "last-admin";
                                    }
                                },
                                pool));
            }
            start.countDown();
            List<String> outcomes = results.stream().map(CompletableFuture::join).toList();

            assertThat(outcomes).containsExactlyInAnyOrder("demoted", "last-admin");
            assertThat(adminCount()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ResultActions send(Account account, MockHttpServletRequestBuilder request, String body)
            throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, account.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    private int adminCount() {
        return jdbc.queryForObject(
                "select count(*) from users where role = 'ADMIN'", Integer.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
