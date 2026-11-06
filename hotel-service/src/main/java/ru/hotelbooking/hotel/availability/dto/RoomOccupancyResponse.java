package ru.hotelbooking.hotel.availability.dto;

import java.math.BigDecimal;

public record RoomOccupancyResponse(
        long roomId,
        long hotelId,
        String number,
        boolean available,
        long timesBooked,
        long allocationCount,
        long occupiedRoomNights,
        long occupiedAvailableRoomNights,
        long capacityRoomNights,
        BigDecimal occupancyPercent) {}
