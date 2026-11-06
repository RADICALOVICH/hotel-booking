package ru.hotelbooking.hotel.catalog.exception;

public class HotelNotFoundException extends RuntimeException {
    public HotelNotFoundException(long id) {
        super("Hotel " + id + " was not found");
    }
}
