package ru.hotelbooking.hotel.availability.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hotelbooking.hotel.availability.dto.AllocationRequest;
import ru.hotelbooking.hotel.availability.dto.AllocationResponse;
import ru.hotelbooking.hotel.availability.entity.AllocationState;
import ru.hotelbooking.hotel.availability.entity.RoomAllocation;
import ru.hotelbooking.hotel.availability.exception.AllocationConflictException;
import ru.hotelbooking.hotel.availability.repository.RoomAllocationRepository;
import ru.hotelbooking.hotel.catalog.entity.Room;
import ru.hotelbooking.hotel.catalog.exception.RoomNotFoundException;
import ru.hotelbooking.hotel.catalog.repository.RoomRepository;

import java.time.Clock;
import java.util.Optional;

/**
 * Confirm and release both lock the room row first. While one transaction holds that lock, no other
 * confirm, release or catalog change of the room can read stale occupancy.
 */
@Service
public class AvailabilityService {
    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);

    private final RoomRepository rooms;
    private final RoomAllocationRepository allocations;
    private final StayDates dates;
    private final Clock clock;

    AvailabilityService(
            RoomRepository rooms,
            RoomAllocationRepository allocations,
            StayDates dates,
            Clock clock) {
        this.rooms = rooms;
        this.allocations = allocations;
        this.dates = dates;
        this.clock = clock;
    }

    @Transactional
    public AllocationResponse confirm(long roomId, AllocationRequest request) {
        dates.requireBookable(request.startDate(), request.endDate());
        Room room = lockRoom(roomId);
        Optional<RoomAllocation> previous = allocations.findByAllocationId(request.allocationId());
        if (previous.isPresent()) {
            RoomAllocation repeated = requireSameAttempt(previous.get(), roomId, request);
            log("allocation_repeat", repeated);
            return AllocationResponse.from(repeated);
        }

        AllocationState state =
                canHold(room, request) ? AllocationState.ACTIVE : AllocationState.REJECTED;
        RoomAllocation allocation = allocations.save(newAllocation(room, request, state));
        if (state == AllocationState.ACTIVE) {
            room.recordAllocation();
        }
        log(
                state == AllocationState.ACTIVE ? "allocation_active" : "allocation_rejected",
                allocation);
        return AllocationResponse.from(allocation);
    }

    @Transactional
    public AllocationResponse release(long roomId, AllocationRequest request) {
        dates.requireOrdered(request.startDate(), request.endDate());
        Room room = lockRoom(roomId);
        Optional<RoomAllocation> previous = allocations.findByAllocationId(request.allocationId());
        if (previous.isEmpty()) {
            // Release overtook its confirm: remember it so the late confirm cannot hold the dates.
            RoomAllocation tombstone =
                    allocations.save(newAllocation(room, request, AllocationState.RELEASED));
            log("allocation_released_before_confirm", tombstone);
            return AllocationResponse.from(tombstone);
        }

        RoomAllocation allocation = requireSameAttempt(previous.get(), roomId, request);
        if (allocation.getState() == AllocationState.ACTIVE) {
            allocation.release(clock.instant());
            room.forgetAllocation();
            log("allocation_released", allocation);
        } else {
            log("allocation_release_repeat", allocation);
        }
        return AllocationResponse.from(allocation);
    }

    private boolean canHold(Room room, AllocationRequest request) {
        return room.isAvailable()
                && !allocations.existsActiveOverlap(
                        room.getId(), request.startDate(), request.endDate());
    }

    private Room lockRoom(long roomId) {
        return rooms.findForUpdateById(roomId).orElseThrow(() -> new RoomNotFoundException(roomId));
    }

    private static RoomAllocation requireSameAttempt(
            RoomAllocation allocation, long roomId, AllocationRequest request) {
        if (!allocation.describes(
                roomId, request.bookingId(), request.startDate(), request.endDate())) {
            throw new AllocationConflictException(
                    "ALLOCATION_MISMATCH",
                    "allocationId is already used with other booking, room or dates");
        }
        return allocation;
    }

    private RoomAllocation newAllocation(
            Room room, AllocationRequest request, AllocationState state) {
        return new RoomAllocation(
                room,
                request.allocationId(),
                request.bookingId(),
                request.startDate(),
                request.endDate(),
                state,
                clock.instant());
    }

    private static void log(String event, RoomAllocation allocation) {
        log.info(
                "event={} bookingId={} allocationId={} roomId={} state={} timesBooked={}",
                event,
                allocation.getBookingId(),
                allocation.getAllocationId(),
                allocation.getRoom().getId(),
                allocation.getState(),
                allocation.getRoom().getTimesBooked());
    }
}
