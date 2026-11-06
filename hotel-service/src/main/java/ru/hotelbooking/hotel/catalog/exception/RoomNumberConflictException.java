package ru.hotelbooking.hotel.catalog.exception;

public class RoomNumberConflictException extends RuntimeException {
    public RoomNumberConflictException(long hotelId) {
        super("The room number already exists in hotel " + hotelId);
    }
}
