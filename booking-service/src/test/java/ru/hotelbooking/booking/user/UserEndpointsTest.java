package ru.hotelbooking.booking.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserEndpointsTest {
    private static final String PASSWORD = "пример-пароля-42";

    private final MockMvc mvc;
    private final ObjectMapper json;

    @Autowired
    UserEndpointsTest(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    @Test
    void registersOnlyAsUserAndAuthenticatesWithNormalizedLogin() throws Exception {
        String created =
                submit(
                                "/user/register",
                                Map.of(
                                        "username",
                                        "  NewGuest ",
                                        "password",
                                        PASSWORD,
                                        "role",
                                        "ADMIN"))
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.username").value("newguest"))
                        .andExpect(jsonPath("$.role").value("USER"))
                        .andExpect(jsonPath("$.password").doesNotExist())
                        .andExpect(jsonPath("$.passwordHash").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String signedIn =
                submit("/user/auth", Map.of("username", "NEWGUEST", "password", PASSWORD))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(json.readTree(signedIn)).isEqualTo(json.readTree(created));
        assertThat(signedIn).doesNotContain(PASSWORD);
    }

    @Test
    void rejectsSecondRegistrationRegardlessOfCase() throws Exception {
        submit("/user/register", Map.of("username", "RepeatGuest", "password", PASSWORD))
                .andExpect(status().isCreated());
        submit("/user/register", Map.of("username", "repeatguest", "password", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void returnsSameErrorForWrongPasswordAndUnknownUsername() throws Exception {
        submit("/user/register", Map.of("username", "known-guest", "password", PASSWORD))
                .andExpect(status().isCreated());

        for (String username : new String[] {"known-guest", "unknown-guest"}) {
            String response =
                    submit(
                                    "/user/auth",
                                    Map.of(
                                            "username",
                                            username,
                                            "password",
                                            "placeholder-credential"))
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                            .andExpect(jsonPath("$.message").value("Invalid username or password"))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertThat(response).doesNotContain(username, "placeholder-credential");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/register", "/user/auth"})
    void validatesInputAndDoesNotEchoThePassword(String path) throws Exception {
        submit(path, Map.of("username", " ", "password", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").isNotEmpty());
        String response =
                submit(path, Map.of("username", "guest", "password", "short-123"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(response).doesNotContain("short-123");
    }

    @Test
    void rejectsMalformedJsonAndNumericPassword() throws Exception {
        mvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        submit("/user/register", Map.of("username", "number-password", "password", 123456789012L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    private ResultActions submit(String path, Map<String, ?> body) throws Exception {
        return mvc.perform(
                post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)));
    }
}
