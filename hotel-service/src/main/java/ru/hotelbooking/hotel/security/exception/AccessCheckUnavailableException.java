package ru.hotelbooking.hotel.security.exception;

public class AccessCheckUnavailableException extends RuntimeException {
    public AccessCheckUnavailableException(String message) {
        super(message);
    }

    public AccessCheckUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
