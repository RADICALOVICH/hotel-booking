package ru.hotelbooking.booking.user.exception;

public class CredentialInputException extends IllegalArgumentException {
    private final String field;

    public CredentialInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
