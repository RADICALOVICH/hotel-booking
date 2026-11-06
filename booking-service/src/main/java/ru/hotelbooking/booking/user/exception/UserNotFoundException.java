package ru.hotelbooking.booking.user.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(long id) {
        super("User " + id + " was not found");
    }
}
