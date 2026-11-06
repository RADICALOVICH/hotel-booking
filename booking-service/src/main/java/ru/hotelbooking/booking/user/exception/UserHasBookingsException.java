package ru.hotelbooking.booking.user.exception;

public class UserHasBookingsException extends RuntimeException {
    public UserHasBookingsException(long id) {
        super("User " + id + " has bookings and cannot be deleted");
    }
}
