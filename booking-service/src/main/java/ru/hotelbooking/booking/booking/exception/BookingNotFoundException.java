package ru.hotelbooking.booking.booking.exception;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(long id) {
        super("Booking " + id + " was not found");
    }
}
