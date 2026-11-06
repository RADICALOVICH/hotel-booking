package ru.hotelbooking.booking.user.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Map;

public record CredentialsRequest(String username, String password) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CredentialsRequest fromJson(Map<String, Object> fields) {
        return new CredentialsRequest(
                JsonText.optional(fields, "username"), JsonText.optional(fields, "password"));
    }

    @Override
    public String toString() {
        return "CredentialsRequest[redacted]";
    }
}
