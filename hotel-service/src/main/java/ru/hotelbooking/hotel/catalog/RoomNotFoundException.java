package ru.hotelbooking.hotel.catalog;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(long id) {
        super("Room " + id + " was not found");
    }
}
