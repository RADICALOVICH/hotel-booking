package ru.hotelbooking.support.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class MvcApiExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        return super.handleExceptionInternal(
                exception, mvcError(exception, status, path), headers, status, request);
    }

    protected static ResponseEntity<ErrorDTO> validationFailure(
            String field, String message, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(validationError(request.getRequestURI(), Map.of(field, message)));
    }

    protected static ResponseEntity<ErrorDTO> response(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(
                        new ErrorDTO(
                                code, message, Instant.now(), request.getRequestURI(), Map.of()));
    }

    private static ErrorDTO mvcError(Exception exception, HttpStatusCode status, String path) {
        if (exception instanceof MethodArgumentNotValidException invalid) {
            Map<String, String> fields = new LinkedHashMap<>();
            for (var field : invalid.getBindingResult().getFieldErrors()) {
                fields.putIfAbsent(field.getField(), field.getDefaultMessage());
            }
            return validationError(path, fields);
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return new ErrorDTO(
                    "INVALID_REQUEST_BODY", "Invalid request body", Instant.now(), path, Map.of());
        }
        HttpStatus standard = HttpStatus.resolve(status.value());
        String message = standard == null ? "Request failed" : standard.getReasonPhrase();
        return new ErrorDTO("HTTP_" + status.value(), message, Instant.now(), path, Map.of());
    }

    private static ErrorDTO validationError(String path, Map<String, String> fields) {
        return new ErrorDTO(
                "VALIDATION_FAILED", "Request validation failed", Instant.now(), path, fields);
    }
}
