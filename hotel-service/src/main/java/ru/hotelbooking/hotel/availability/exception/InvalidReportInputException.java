package ru.hotelbooking.hotel.availability.exception;

public class InvalidReportInputException extends RuntimeException {
    private final String field;

    public InvalidReportInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
