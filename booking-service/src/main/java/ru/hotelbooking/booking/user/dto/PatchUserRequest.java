package ru.hotelbooking.booking.user.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Map;
import java.util.Set;

public record PatchUserRequest(String username, String password, String role) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PatchUserRequest fromJson(Map<String, Object> fields) {
        JsonText.requireOnly(fields, Set.of("username", "password", "role"));
        return new PatchUserRequest(
                JsonText.presentOrAbsent(fields, "username"),
                JsonText.presentOrAbsent(fields, "password"),
                JsonText.presentOrAbsent(fields, "role"));
    }

    @Override
    public String toString() {
        return "PatchUserRequest[redacted]";
    }
}
