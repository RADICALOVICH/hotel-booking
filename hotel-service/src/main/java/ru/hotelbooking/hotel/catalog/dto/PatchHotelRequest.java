package ru.hotelbooking.hotel.catalog.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record PatchHotelRequest(
        @Size(max = 255)
                @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "must not be blank")
                String name,
        @Size(max = 255)
                @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "must not be blank")
                String address) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PatchHotelRequest fromJson(Map<String, Object> fields) {
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("At least one hotel field is required");
        }
        for (String field : fields.keySet()) {
            if (!field.equals("name") && !field.equals("address")) {
                throw new IllegalArgumentException("Unknown hotel field");
            }
        }
        return new PatchHotelRequest(readText(fields, "name"), readText(fields, "address"));
    }

    private static String readText(Map<String, Object> fields, String key) {
        if (!fields.containsKey(key)) {
            return null;
        }
        if (fields.get(key) instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException(key + " must be a string");
    }
}
