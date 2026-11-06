package ru.hotelbooking.hotel.catalog;

public class HotelNotFoundException extends RuntimeException {
    public HotelNotFoundException(long id) {
        super("Hotel " + id + " was not found");
    }
}
