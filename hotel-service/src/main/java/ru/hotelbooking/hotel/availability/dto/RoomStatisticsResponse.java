package ru.hotelbooking.hotel.availability.dto;

public record RoomStatisticsResponse(
        long roomId,
        long hotelId,
        String number,
        boolean available,
        long timesBooked,
        long activeAllocations) {}
