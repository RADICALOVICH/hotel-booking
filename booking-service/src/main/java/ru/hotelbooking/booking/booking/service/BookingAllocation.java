package ru.hotelbooking.booking.booking.service;

import ru.hotelbooking.booking.booking.client.AllocationRequest;
import ru.hotelbooking.booking.booking.dto.BookingResponse;
import ru.hotelbooking.support.allocation.AllocationIds;

record BookingAllocation(long roomId, AllocationRequest request) {
    static BookingAllocation from(BookingResponse booking) {
        long roomId = booking.roomId();
        String allocationId = AllocationIds.forBookingRoom(booking.id(), roomId);
        return new BookingAllocation(
                roomId,
                new AllocationRequest(
                        booking.id(), allocationId, booking.startDate(), booking.endDate()));
    }
}
