package ru.hotelbooking.booking.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ru.hotelbooking.booking.security.TestJwtKeys;
import ru.hotelbooking.booking.support.TestAccounts;
import ru.hotelbooking.booking.support.TestAccounts.Account;
import ru.hotelbooking.booking.user.service.UserService;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestJwtKeys.class, UserConcurrencyTest.PasswordConfiguration.class})
@ActiveProfiles("test")
class UserConcurrencyTest {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final String NEW_PASSWORD = "changed-password-12345";

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final TestAccounts accounts;
    private final GatedPasswordEncoder passwords;

    @Autowired
    UserConcurrencyTest(
            MockMvc mvc, ObjectMapper json, UserService users, GatedPasswordEncoder passwords) {
        this.mvc = mvc;
        this.json = json;
        this.accounts = new TestAccounts(mvc, json, users);
        this.passwords = passwords;
    }

    @ParameterizedTest
    @CsvSource({"role, USER", "username, renamed-concurrent-admin"})
    void stalePasswordUpdateCannotOverwriteAnotherChange(String field, String value)
            throws Exception {
        Account actor = accounts.admin("concurrent-actor-" + NAMES.incrementAndGet());
        String username = "concurrent-target-" + NAMES.incrementAndGet();
        Account target = accounts.admin(username);
        String path = "/user/" + target.id();

        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            Gate gate = passwords.pauseNextEncoding();
            var changePassword =
                    workers.submit(() -> update(actor, path, Map.of("password", NEW_PASSWORD)));
            try {
                // Password encoding starts only after update() has read the original user row.
                assertThat(gate.entered.await(5, TimeUnit.SECONDS)).isTrue();
                update(actor, path, Map.of(field, value))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$." + field).value(value));
            } finally {
                gate.proceed.countDown();
            }

            changePassword
                    .get(5, TimeUnit.SECONDS)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, actor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + field).value(value));
        mvc.perform(
                        get("/internal/auth/validate")
                                .header(HttpHeaders.AUTHORIZATION, target.bearer()))
                .andExpect(status().is(field.equals("role") ? 401 : 204));

        String currentUsername = field.equals("username") ? value : username;
        accounts.login(currentUsername, TestAccounts.PASSWORD);
        // A fresh retry can change the password without undoing the other field.
        update(actor, path, Map.of("password", NEW_PASSWORD)).andExpect(status().isOk());
        Account renewed = accounts.login(currentUsername, NEW_PASSWORD);
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, actor.bearer()))
                .andExpect(jsonPath("$." + field).value(value));
        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, renewed.bearer()))
                .andExpect(status().is(field.equals("role") ? 403 : 200));
    }

    private ResultActions update(Account actor, String path, Map<String, String> body)
            throws Exception {
        return mvc.perform(
                patch(path)
                        .header(HttpHeaders.AUTHORIZATION, actor.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PasswordConfiguration {
        @Bean
        @Primary
        GatedPasswordEncoder gatedPasswordEncoder(
                @Qualifier("passwordEncoder") PasswordEncoder delegate) {
            return new GatedPasswordEncoder(delegate);
        }
    }

    static class GatedPasswordEncoder implements PasswordEncoder {
        private final PasswordEncoder delegate;
        private final AtomicReference<Gate> next = new AtomicReference<>();

        GatedPasswordEncoder(PasswordEncoder delegate) {
            this.delegate = delegate;
        }

        Gate pauseNextEncoding() {
            Gate gate = new Gate();
            next.set(gate);
            return gate;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            Gate gate = next.getAndSet(null);
            if (gate != null) {
                gate.entered.countDown();
                try {
                    if (!gate.proceed.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent update");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
            }
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return delegate.matches(rawPassword, encodedPassword);
        }
    }

    private static class Gate {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch proceed = new CountDownLatch(1);
    }
}
