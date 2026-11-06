package ru.hotelbooking.hotel.availability.exception;

/** 409 for a confirm or release that cannot hold the dates; the code tells Booking why. */
public class AllocationConflictException extends RuntimeException {
    private final String code;

    public AllocationConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
