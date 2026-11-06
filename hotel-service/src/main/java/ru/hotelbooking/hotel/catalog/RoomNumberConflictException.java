package ru.hotelbooking.hotel.catalog;

public class RoomNumberConflictException extends RuntimeException {
    public RoomNumberConflictException(long hotelId) {
        super("The room number already exists in hotel " + hotelId);
    }
}
