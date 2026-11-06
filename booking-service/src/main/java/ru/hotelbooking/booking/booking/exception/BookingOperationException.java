package ru.hotelbooking.booking.booking.exception;

public class BookingOperationException extends RuntimeException {
    private final int status;
    private final String code;
    private final Long bookingId;

    public BookingOperationException(int status, String code, String message, Long bookingId) {
        super(message);
        this.status = status;
        this.code = code;
        this.bookingId = bookingId;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Long bookingId() {
        return bookingId;
    }
}
