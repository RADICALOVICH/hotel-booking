package ru.hotelbooking.hotel.catalog.dto;

public record RoomResponse(
        long id, long hotelId, String number, boolean available, long timesBooked) {}
