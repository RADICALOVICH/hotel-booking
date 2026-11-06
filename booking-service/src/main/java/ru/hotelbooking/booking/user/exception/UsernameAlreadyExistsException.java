package ru.hotelbooking.booking.user.exception;

public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException() {
        super("Username is already in use");
    }

    public UsernameAlreadyExistsException(Throwable cause) {
        super("Username is already in use", cause);
    }
}
