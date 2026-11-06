package ru.hotelbooking.booking.user;

public class InvalidCredentialsException extends RuntimeException {
    InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
