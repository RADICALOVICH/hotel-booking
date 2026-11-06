package ru.hotelbooking.hotel.availability.dto;

import java.math.BigDecimal;

public record HotelOccupancyResponse(
        long hotelId,
        String hotelName,
        long roomCount,
        long availableRoomCount,
        long allocationCount,
        long occupiedRoomNights,
        long occupiedAvailableRoomNights,
        long capacityRoomNights,
        BigDecimal occupancyPercent,
        long timesBookedSpread) {}
