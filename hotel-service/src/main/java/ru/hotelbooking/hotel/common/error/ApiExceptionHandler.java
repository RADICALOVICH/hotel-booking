package ru.hotelbooking.hotel.common.error;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.hotelbooking.hotel.availability.exception.AllocationConflictException;
import ru.hotelbooking.hotel.availability.exception.BookingOccupancyUnavailableException;
import ru.hotelbooking.hotel.availability.exception.InvalidReportInputException;
import ru.hotelbooking.hotel.availability.exception.InvalidStayDatesException;
import ru.hotelbooking.hotel.catalog.exception.HotelNotFoundException;
import ru.hotelbooking.hotel.catalog.exception.RoomInUseException;
import ru.hotelbooking.hotel.catalog.exception.RoomNotFoundException;
import ru.hotelbooking.hotel.catalog.exception.RoomNumberConflictException;
import ru.hotelbooking.support.web.ErrorDTO;
import ru.hotelbooking.support.web.MvcApiExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends MvcApiExceptionHandler {
    @ExceptionHandler(BookingOccupancyUnavailableException.class)
    public ResponseEntity<ErrorDTO> occupancyUnavailable(
            BookingOccupancyUnavailableException exception, HttpServletRequest request) {
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "BOOKING_OCCUPANCY_UNAVAILABLE",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(HotelNotFoundException.class)
    public ResponseEntity<ErrorDTO> hotelNotFound(
            HotelNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "HOTEL_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorDTO> roomNotFound(
            RoomNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(RoomNumberConflictException.class)
    public ResponseEntity<ErrorDTO> roomNumberConflict(
            RoomNumberConflictException exception, HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT, "ROOM_NUMBER_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(RoomInUseException.class)
    public ResponseEntity<ErrorDTO> roomInUse(
            RoomInUseException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(AllocationConflictException.class)
    public ResponseEntity<ErrorDTO> allocationConflict(
            AllocationConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidStayDatesException.class)
    public ResponseEntity<ErrorDTO> invalidStayDates(
            InvalidStayDatesException exception, HttpServletRequest request) {
        return validationFailure(exception.getField(), exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidReportInputException.class)
    public ResponseEntity<ErrorDTO> invalidReportInput(
            InvalidReportInputException exception, HttpServletRequest request) {
        return validationFailure(exception.getField(), exception.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDTO> integrityConflict(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_CONFLICT",
                "The operation conflicts with a database constraint",
                request);
    }
}
