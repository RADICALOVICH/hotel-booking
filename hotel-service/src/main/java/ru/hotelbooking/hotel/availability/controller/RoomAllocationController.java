package ru.hotelbooking.hotel.availability.controller;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.hotelbooking.hotel.availability.dto.AllocationRequest;
import ru.hotelbooking.hotel.availability.dto.AllocationResponse;
import ru.hotelbooking.hotel.availability.exception.AllocationConflictException;
import ru.hotelbooking.hotel.availability.service.AvailabilityService;

/** Internal steps of the booking saga; only Booking's service token may call them. */
@RestController
@RequestMapping("/api/rooms/{id}")
@PreAuthorize("hasRole('SERVICE')")
public class RoomAllocationController {
    private final AvailabilityService availability;

    public RoomAllocationController(AvailabilityService availability) {
        this.availability = availability;
    }

    @PostMapping("/confirm-availability")
    public AllocationResponse confirm(
            @PathVariable long id, @Valid @RequestBody AllocationRequest request) {
        AllocationResponse allocation = availability.confirm(id, request);
        return switch (allocation.state()) {
            case ACTIVE -> allocation;
            case REJECTED ->
                    throw new AllocationConflictException(
                            "ROOM_UNAVAILABLE", "The room cannot be held for these dates");
            case RELEASED ->
                    throw new AllocationConflictException(
                            "ALLOCATION_RELEASED", "This allocation was already released");
        };
    }

    @PostMapping("/release")
    public AllocationResponse release(
            @PathVariable long id, @Valid @RequestBody AllocationRequest request) {
        return availability.release(id, request);
    }
}
