package ru.hotelbooking.hotel.availability.exception;

public class InvalidStayDatesException extends RuntimeException {
    private final String field;

    public InvalidStayDatesException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
