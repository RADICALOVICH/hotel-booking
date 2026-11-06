package ru.hotelbooking.booking.user;

public class CredentialInputException extends IllegalArgumentException {
    private final String field;

    CredentialInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
