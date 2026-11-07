package ru.hotelbooking.hotel.availability.service;

import org.springframework.stereotype.Service;

import ru.hotelbooking.hotel.availability.client.BookingOccupancy;
import ru.hotelbooking.hotel.availability.dto.AllocationRequest;
import ru.hotelbooking.hotel.availability.dto.AllocationResponse;

/** Read remote occupancy before opening the local room-lock transaction. */
@Service
public class AvailabilityService {
    private final AllocationTransactions allocations;
    private final BookingOccupancy occupancy;
    private final StayDates dates;

    AvailabilityService(
            AllocationTransactions allocations, BookingOccupancy occupancy, StayDates dates) {
        this.allocations = allocations;
        this.occupancy = occupancy;
        this.dates = dates;
    }

    public AllocationResponse confirm(long roomId, AllocationRequest request) {
        dates.requireBookable(request.startDate(), request.endDate());
        var previous = allocations.previous(roomId, request);
        if (previous.isPresent()) {
            return previous.get();
        }
        boolean booked =
                occupancy.find(request.startDate(), request.endDate()).stream()
                        .anyMatch(stay -> stay.roomId() == roomId);
        return allocations.confirm(roomId, request, booked);
    }

    public AllocationResponse release(long roomId, AllocationRequest request) {
        dates.requireOrdered(request.startDate(), request.endDate());
        return allocations.release(roomId, request);
    }
}
