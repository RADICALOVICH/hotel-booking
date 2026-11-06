package ru.hotelbooking.hotel.availability.dto;

import ru.hotelbooking.hotel.availability.entity.AllocationState;
import ru.hotelbooking.hotel.availability.entity.RoomAllocation;

import java.time.LocalDate;

public record AllocationResponse(
        String allocationId,
        long bookingId,
        long roomId,
        LocalDate startDate,
        LocalDate endDate,
        AllocationState state) {
    public static AllocationResponse from(RoomAllocation allocation) {
        return new AllocationResponse(
                allocation.getAllocationId(),
                allocation.getBookingId(),
                allocation.getRoom().getId(),
                allocation.getStartDate(),
                allocation.getEndDate(),
                allocation.getState());
    }
}
