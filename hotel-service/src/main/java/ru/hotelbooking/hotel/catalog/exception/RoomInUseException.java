package ru.hotelbooking.hotel.catalog.exception;

/** 409 when a room change would break current stays or erase allocation history. */
public class RoomInUseException extends RuntimeException {
    private final String code;

    public RoomInUseException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
