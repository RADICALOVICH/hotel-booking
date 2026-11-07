package ru.hotelbooking.hotel.availability.exception;

public class BookingOccupancyUnavailableException extends RuntimeException {
    public BookingOccupancyUnavailableException(Throwable cause) {
        super("Booking occupancy is temporarily unavailable", cause);
    }
}
