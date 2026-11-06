package ru.hotelbooking.booking.user.dto;

import java.util.Map;
import java.util.Set;

final class JsonText {
    private JsonText() {}

    static String optional(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null || value instanceof String) {
            return (String) value;
        }
        throw new IllegalArgumentException("Expected a string for " + key);
    }

    static String presentOrAbsent(Map<String, Object> fields, String key) {
        if (fields.containsKey(key) && fields.get(key) == null) {
            throw new IllegalArgumentException(key + " must not be null");
        }
        return optional(fields, key);
    }

    static void requireOnly(Map<String, Object> fields, Set<String> allowed) {
        if (fields.isEmpty() || !allowed.containsAll(fields.keySet())) {
            throw new IllegalArgumentException("Expected one or more of " + allowed);
        }
    }
}
