package ru.hotelbooking.booking.booking.client;

public record RecommendedRoom(
        long id, long hotelId, String number, boolean available, long timesBooked) {}
