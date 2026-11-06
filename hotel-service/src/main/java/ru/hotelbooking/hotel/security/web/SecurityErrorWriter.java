package ru.hotelbooking.hotel.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ru.hotelbooking.support.web.ServletSecurityErrors;

import java.io.IOException;

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
        ServletSecurityErrors.write(json, request, response, status, code, message);
    }
}
