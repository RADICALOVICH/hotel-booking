package ru.hotelbooking.booking.common.error;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.hotelbooking.booking.booking.exception.BookingInputException;
import ru.hotelbooking.booking.booking.exception.BookingNotFoundException;
import ru.hotelbooking.booking.booking.exception.BookingOperationException;
import ru.hotelbooking.booking.user.exception.InvalidCredentialsException;
import ru.hotelbooking.booking.user.exception.LastAdminException;
import ru.hotelbooking.booking.user.exception.UserHasBookingsException;
import ru.hotelbooking.booking.user.exception.UserInputException;
import ru.hotelbooking.booking.user.exception.UserNotFoundException;
import ru.hotelbooking.booking.user.exception.UsernameAlreadyExistsException;
import ru.hotelbooking.support.web.ErrorDTO;
import ru.hotelbooking.support.web.MvcApiExceptionHandler;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends MvcApiExceptionHandler {
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorDTO> concurrentModification(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "The resource changed during this request; reload it and retry",
                request);
    }

    @ExceptionHandler(UserInputException.class)
    public ResponseEntity<ErrorDTO> invalidInput(
            UserInputException exception, HttpServletRequest request) {
        return validationFailure(exception.getField(), exception.getMessage(), request);
    }

    @ExceptionHandler(BookingInputException.class)
    public ResponseEntity<ErrorDTO> invalidBookingInput(
            BookingInputException exception, HttpServletRequest request) {
        return validationFailure(exception.field(), exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorDTO> invalidCredentials(
            InvalidCredentialsException exception, HttpServletRequest request) {
        return response(
                HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage(), request);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorDTO> usernameConflict(
            UsernameAlreadyExistsException exception, HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", exception.getMessage(), request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorDTO> userNotFound(
            UserNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(UserHasBookingsException.class)
    public ResponseEntity<ErrorDTO> userHasBookings(
            UserHasBookingsException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "USER_HAS_BOOKINGS", exception.getMessage(), request);
    }

    @ExceptionHandler(LastAdminException.class)
    public ResponseEntity<ErrorDTO> lastAdmin(
            LastAdminException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "LAST_ADMIN", exception.getMessage(), request);
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ErrorDTO> bookingNotFound(
            BookingNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", exception.getMessage(), request);
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
}
