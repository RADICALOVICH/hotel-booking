package ru.hotelbooking.booking.user.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Map;
import java.util.Set;

public record CreateUserRequest(String username, String password, String role) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CreateUserRequest fromJson(Map<String, Object> fields) {
        JsonText.requireOnly(fields, Set.of("username", "password", "role"));
        return new CreateUserRequest(
                JsonText.optional(fields, "username"),
                JsonText.optional(fields, "password"),
                JsonText.optional(fields, "role"));
    }

    @Override
    public String toString() {
        return "CreateUserRequest[redacted]";
    }
}
