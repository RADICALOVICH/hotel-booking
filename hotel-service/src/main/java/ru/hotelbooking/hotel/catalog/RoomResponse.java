package ru.hotelbooking.hotel.catalog;

public record RoomResponse(
        long id, long hotelId, String number, boolean available, long timesBooked) {}
