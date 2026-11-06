package ru.hotelbooking.hotel.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import ru.hotelbooking.hotel.common.error.ErrorDTO;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/** Writes security failures, which happen before controllers, in the common ErrorDTO form. */
@Component
public class SecurityErrorWriter {
    private final ObjectMapper json;

    SecurityErrorWriter(ObjectMapper json) {
        this.json = json;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message)
            throws IOException {
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        json.writeValue(
                response.getOutputStream(),
                new ErrorDTO(code, message, Instant.now(), request.getRequestURI(), Map.of()));
    }
}
