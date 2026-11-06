package ru.hotelbooking.booking.user;

public class UsernameAlreadyExistsException extends RuntimeException {
    UsernameAlreadyExistsException() {
        super("Username is already in use");
    }

    UsernameAlreadyExistsException(Throwable cause) {
        super("Username is already in use", cause);
    }
}
