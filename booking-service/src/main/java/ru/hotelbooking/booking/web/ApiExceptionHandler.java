package ru.hotelbooking.booking.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import ru.hotelbooking.booking.user.CredentialInputException;
import ru.hotelbooking.booking.user.InvalidCredentialsException;
import ru.hotelbooking.booking.user.UsernameAlreadyExistsException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(CredentialInputException.class)
    public ResponseEntity<ErrorDTO> invalidInput(
            CredentialInputException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        error(
                                "VALIDATION_FAILED",
                                "Request validation failed",
                                request.getRequestURI(),
                                Map.of(exception.getField(), exception.getMessage())));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorDTO> invalidCredentials(
            InvalidCredentialsException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(
                        error(
                                "INVALID_CREDENTIALS",
                                exception.getMessage(),
                                request.getRequestURI(),
                                Map.of()));
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorDTO> usernameConflict(
            UsernameAlreadyExistsException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        error(
                                "USERNAME_ALREADY_EXISTS",
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
        ErrorDTO response;
        if (exception instanceof HttpMessageNotReadableException) {
            response = error("INVALID_REQUEST_BODY", "Invalid request body", path, Map.of());
        } else {
            HttpStatus standard = HttpStatus.resolve(status.value());
            String message = standard == null ? "Request failed" : standard.getReasonPhrase();
            response = error("HTTP_" + status.value(), message, path, Map.of());
        }
        return super.handleExceptionInternal(exception, response, headers, status, request);
    }

    private static ErrorDTO error(
            String code, String message, String path, Map<String, String> fields) {
        return new ErrorDTO(code, message, Instant.now(), path, fields);
    }
}
