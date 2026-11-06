package ru.hotelbooking.booking.booking.service;

import ru.hotelbooking.booking.booking.client.AllocationRequest;
import ru.hotelbooking.booking.booking.dto.BookingResponse;

record BookingAllocation(long roomId, AllocationRequest request) {
    static BookingAllocation from(BookingResponse booking) {
        long roomId = booking.roomId();
        String allocationId = "booking-" + booking.id() + "-room-" + roomId;
        return new BookingAllocation(
                roomId,
                new AllocationRequest(
                        booking.id(), allocationId, booking.startDate(), booking.endDate()));
    }
}
