package ru.hotelbooking.support.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import ru.hotelbooking.support.trace.TraceIds;

import java.time.Instant;
import java.util.Map;

public record ErrorDTO(
        String code,
        String message,
        Instant timestamp,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fieldErrors,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long bookingId,
        String traceId) {
    public ErrorDTO(
            String code,
            String message,
            Instant timestamp,
            String path,
            Map<String, String> fieldErrors) {
        this(code, message, timestamp, path, fieldErrors, null);
    }

    public ErrorDTO(
            String code,
            String message,
            Instant timestamp,
            String path,
            Map<String, String> fieldErrors,
            Long bookingId) {
        this(code, message, timestamp, path, fieldErrors, bookingId, TraceIds.current());
    }
}
