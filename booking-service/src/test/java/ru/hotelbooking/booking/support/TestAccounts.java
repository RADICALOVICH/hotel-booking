package ru.hotelbooking.booking.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.hotelbooking.booking.user.entity.UserRole;
import ru.hotelbooking.booking.user.service.UserService;

import java.util.Map;

/** Creates accounts and signs them in through the real API to obtain real JWTs. */
public final class TestAccounts {
    public static final String PASSWORD = "test-password-42";

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final UserService users;

    public TestAccounts(MockMvc mvc, ObjectMapper json, UserService users) {
        this.mvc = mvc;
        this.json = json;
        this.users = users;
    }

    public Account user(String username) throws Exception {
        JsonNode body =
                json.readTree(
                        mvc.perform(credentials("/user/register", username, PASSWORD))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray());
        return new Account(body.path("id").asLong(), body.path("accessToken").asText());
    }

    public Account admin(String username) throws Exception {
        users.createAccount(username, PASSWORD, UserRole.ADMIN);
        return login(username, PASSWORD);
    }

    public Account login(String username, String password) throws Exception {
        JsonNode body =
                json.readTree(
                        mvc.perform(credentials("/user/auth", username, password))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray());
        return new Account(body.path("id").asLong(), body.path("accessToken").asText());
    }

    private MockHttpServletRequestBuilder credentials(String path, String username, String password)
            throws Exception {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        json.writeValueAsString(
                                Map.of("username", username, "password", password)));
    }

    public record Account(long id, String token) {
        public String bearer() {
            return "Bearer " + token;
        }

        @Override
        public String toString() {
            return "Account[id=" + id + "]";
        }
    }
}
