package ru.hotelbooking.booking.booking.exception;

public class BookingInputException extends RuntimeException {
    private final String field;

    public BookingInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
