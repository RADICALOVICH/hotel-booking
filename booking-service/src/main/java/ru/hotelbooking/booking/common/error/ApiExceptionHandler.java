package ru.hotelbooking.booking.common.error;

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

import ru.hotelbooking.booking.booking.exception.BookingInputException;
import ru.hotelbooking.booking.booking.exception.BookingNotFoundException;
import ru.hotelbooking.booking.booking.exception.BookingOperationException;
import ru.hotelbooking.booking.user.exception.InvalidCredentialsException;
import ru.hotelbooking.booking.user.exception.LastAdminException;
import ru.hotelbooking.booking.user.exception.UserHasBookingsException;
import ru.hotelbooking.booking.user.exception.UserInputException;
import ru.hotelbooking.booking.user.exception.UserNotFoundException;
import ru.hotelbooking.booking.user.exception.UsernameAlreadyExistsException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(UserInputException.class)
    public ResponseEntity<ErrorDTO> invalidInput(
            UserInputException exception, HttpServletRequest request) {
        return invalidInput(exception.getField(), exception.getMessage(), request);
    }

    @ExceptionHandler(BookingInputException.class)
    public ResponseEntity<ErrorDTO> invalidBookingInput(
            BookingInputException exception, HttpServletRequest request) {
        return invalidInput(exception.field(), exception.getMessage(), request);
    }

    private static ResponseEntity<ErrorDTO> invalidInput(
            String field, String message, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        error(
                                "VALIDATION_FAILED",
                                "Request validation failed",
                                request.getRequestURI(),
                                Map.of(field, message)));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorDTO> invalidCredentials(
            InvalidCredentialsException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception, request);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorDTO> usernameConflict(
            UsernameAlreadyExistsException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", exception, request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorDTO> userNotFound(
            UserNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception, request);
    }

    @ExceptionHandler(UserHasBookingsException.class)
    public ResponseEntity<ErrorDTO> userHasBookings(
            UserHasBookingsException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "USER_HAS_BOOKINGS", exception, request);
    }

    @ExceptionHandler(LastAdminException.class)
    public ResponseEntity<ErrorDTO> lastAdmin(
            LastAdminException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "LAST_ADMIN", exception, request);
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ErrorDTO> bookingNotFound(
            BookingNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", exception, request);
    }

    @ExceptionHandler(BookingOperationException.class)
    public ResponseEntity<ErrorDTO> bookingOperationFailed(
            BookingOperationException exception, HttpServletRequest request) {
        var response = ResponseEntity.status(exception.status());
        if (exception.status() == HttpStatus.UNAUTHORIZED.value()) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(
                new ErrorDTO(
                        exception.code(),
                        exception.getMessage(),
                        Instant.now(),
                        request.getRequestURI(),
                        Map.of(),
                        exception.bookingId()));
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

    private static ResponseEntity<ErrorDTO> response(
            HttpStatus status, String code, Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(error(code, exception.getMessage(), request.getRequestURI(), Map.of()));
    }

    private static ErrorDTO error(
            String code, String message, String path, Map<String, String> fields) {
        return new ErrorDTO(code, message, Instant.now(), path, fields);
    }
}
