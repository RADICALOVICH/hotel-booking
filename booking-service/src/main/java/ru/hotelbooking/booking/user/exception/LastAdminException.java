package ru.hotelbooking.booking.user.exception;

public class LastAdminException extends RuntimeException {
    public LastAdminException() {
        super("At least one ADMIN must remain");
    }
}
