package ru.hotelbooking.booking.user.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record CredentialsRequest(
        String username, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CredentialsRequest fromJson(Map<String, Object> fields) {
        return new CredentialsRequest(readText(fields, "username"), readText(fields, "password"));
    }

    private static String readText(Map<String, Object> fields, String field) {
        Object value = fields.get(field);
        if (value == null || value instanceof String) {
            return (String) value;
        }
        throw new IllegalArgumentException("Expected a string for " + field);
    }

    @Override
    public String toString() {
        return "CredentialsRequest[redacted]";
    }
}
