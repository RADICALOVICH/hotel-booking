package ru.hotelbooking.hotel.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

public record ErrorDTO(
        String code,
        String message,
        Instant timestamp,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fieldErrors,
        String traceId) {
    public ErrorDTO(
            String code,
            String message,
            Instant timestamp,
            String path,
            Map<String, String> fieldErrors) {
        this(code, message, timestamp, path, fieldErrors, MDC.get("traceId"));
    }
}
