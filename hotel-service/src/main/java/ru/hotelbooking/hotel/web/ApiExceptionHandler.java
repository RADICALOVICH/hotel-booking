package ru.hotelbooking.hotel.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import ru.hotelbooking.hotel.catalog.HotelNotFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(HotelNotFoundException.class)
    public ResponseEntity<ErrorDTO> hotelNotFound(
            HotelNotFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        error(
                                "HOTEL_NOT_FOUND",
                                exception.getMessage(),
                                request.getRequestURI(),
                                Map.of()));
    }

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

    private static ErrorDTO mvcError(Exception exception, HttpStatusCode status, String path) {
        if (exception instanceof MethodArgumentNotValidException invalid) {
            Map<String, String> fields = new LinkedHashMap<>();
            for (var field : invalid.getBindingResult().getFieldErrors()) {
                fields.putIfAbsent(field.getField(), field.getDefaultMessage());
            }
            return error("VALIDATION_FAILED", "Request validation failed", path, fields);
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return error("INVALID_REQUEST_BODY", "Invalid request body", path, Map.of());
        }
        HttpStatus standard = HttpStatus.resolve(status.value());
        String message = standard == null ? "Request failed" : standard.getReasonPhrase();
        return error("HTTP_" + status.value(), message, path, Map.of());
    }

    private static ErrorDTO error(
            String code, String message, String path, Map<String, String> fields) {
        return new ErrorDTO(code, message, Instant.now(), path, fields);
    }
}
