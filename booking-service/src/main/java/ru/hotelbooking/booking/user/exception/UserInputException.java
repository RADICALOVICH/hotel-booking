package ru.hotelbooking.booking.user.exception;

public class UserInputException extends IllegalArgumentException {
    private final String field;

    public UserInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
