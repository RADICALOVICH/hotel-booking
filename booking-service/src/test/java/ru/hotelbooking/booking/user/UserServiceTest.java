package ru.hotelbooking.booking.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import ru.hotelbooking.booking.security.TestJwtKeys;
import ru.hotelbooking.booking.user.dto.UserResponse;
import ru.hotelbooking.booking.user.entity.UserRole;
import ru.hotelbooking.booking.user.exception.UsernameAlreadyExistsException;
import ru.hotelbooking.booking.user.service.UserService;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(TestJwtKeys.class)
@ActiveProfiles("test")
class UserServiceTest {
    private static final String TEST_PASSWORD = "пример-пароля-42";

    private final UserService users;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Autowired
    UserServiceTest(
            UserService users, PasswordEncoder passwords, JdbcTemplate jdbc, ObjectMapper json) {
        this.users = users;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Test
    void persistsSaltedHashAndReturnsOnlyPublicFields() throws Exception {
        UserResponse first = users.register("  IvAn  ", TEST_PASSWORD).user();
        UserResponse second = users.register("other-guest", TEST_PASSWORD).user();

        String firstHash = storedHash(first.id());
        String secondHash = storedHash(second.id());
        assertThat(firstHash).isNotBlank().isNotEqualTo(TEST_PASSWORD).isNotEqualTo(secondHash);
        assertThat(passwords.matches(TEST_PASSWORD, firstHash)).isTrue();
        assertThat(passwords.matches(TEST_PASSWORD, secondHash)).isTrue();
        assertThat(passwords.matches("другой пароль", firstHash)).isFalse();
        assertThat(first.id()).isPositive();
        assertThat(first.username()).isEqualTo("ivan");
        assertThat(first.role()).isEqualTo(UserRole.USER);
        assertThat(json.readTree(json.writeValueAsString(first)))
                .isEqualTo(
                        json.readTree(
                                "{\"id\":"
                                        + first.id()
                                        + ",\"username\":\"ivan\",\"role\":\"USER\"}"));
    }

    @Test
    void rejectsDuplicateLoginInServiceAndDatabase() {
        UserResponse user = users.register("  Marina ", TEST_PASSWORD).user();

        assertThatThrownBy(() -> users.register("MARINA", TEST_PASSWORD))
                .isInstanceOf(UsernameAlreadyExistsException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into users (username, password, role) values (?, ?, ?)",
                                        user.username(),
                                        storedHash(user.id()),
                                        "USER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "12345678901"})
    void requiresNonBlankPasswordOfAtLeastTwelveCharacters(String password) {
        assertThatThrownBy(() -> users.register("valid-login", password))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankLoginAndAcceptsTwelveCharacterPassword() {
        assertThatThrownBy(() -> users.register(" \t ", TEST_PASSWORD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(users.register("twelve-char-test", "abcdef123456").user().id()).isPositive();
    }

    private String storedHash(long id) {
        return jdbc.queryForObject("select password from users where id = ?", String.class, id);
    }
}
