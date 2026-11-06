package ru.hotelbooking.hotel.catalog.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;

public record PatchRoomRequest(
        @Size(max = 64)
                @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "must not be blank")
                String number,
        Boolean available) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PatchRoomRequest fromJson(Map<String, Object> fields) {
        if (fields.isEmpty() || !Set.of("number", "available").containsAll(fields.keySet())) {
            throw new IllegalArgumentException("Only number and available can be updated");
        }
        return new PatchRoomRequest(
                readField(fields, "number", String.class),
                readField(fields, "available", Boolean.class));
    }

    private static <T> T readField(Map<String, Object> fields, String key, Class<T> type) {
        if (!fields.containsKey(key)) {
            return null;
        }
        Object value = fields.get(key);
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Invalid value for " + key);
        }
        return type.cast(value);
    }
}
