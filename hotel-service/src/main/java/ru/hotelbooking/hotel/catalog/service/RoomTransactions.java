package ru.hotelbooking.hotel.catalog.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hotelbooking.hotel.availability.entity.AllocationState;
import ru.hotelbooking.hotel.availability.repository.RoomAllocationRepository;
import ru.hotelbooking.hotel.availability.service.StayDates;
import ru.hotelbooking.hotel.catalog.dto.CreateRoomRequest;
import ru.hotelbooking.hotel.catalog.dto.PatchRoomRequest;
import ru.hotelbooking.hotel.catalog.dto.RoomResponse;
import ru.hotelbooking.hotel.catalog.entity.Hotel;
import ru.hotelbooking.hotel.catalog.entity.Room;
import ru.hotelbooking.hotel.catalog.exception.HotelNotFoundException;
import ru.hotelbooking.hotel.catalog.exception.RoomInUseException;
import ru.hotelbooking.hotel.catalog.exception.RoomNotFoundException;
import ru.hotelbooking.hotel.catalog.exception.RoomNumberConflictException;
import ru.hotelbooking.hotel.catalog.repository.HotelRepository;
import ru.hotelbooking.hotel.catalog.repository.RoomRepository;

import java.util.Objects;

@Service
class RoomTransactions {
    private final RoomRepository rooms;
    private final HotelRepository hotels;
    private final RoomAllocationRepository allocations;
    private final StayDates dates;

    public RoomTransactions(
            RoomRepository rooms,
            HotelRepository hotels,
            RoomAllocationRepository allocations,
            StayDates dates) {
        this.rooms = rooms;
        this.hotels = hotels;
        this.allocations = allocations;
        this.dates = dates;
    }

    @Transactional
    public RoomResponse create(CreateRoomRequest request) {
        long hotelId = request.hotelId();
        Hotel hotel =
                hotels.findForUpdateById(hotelId)
                        .orElseThrow(() -> new HotelNotFoundException(hotelId));
        String number = request.number().strip();
        requireFreeNumber(hotelId, number);
        return RoomResponse.from(rooms.save(new Room(hotel, number)));
    }

    @Transactional(readOnly = true)
    public RoomResponse get(long id) {
        return rooms.findById(id)
                .map(RoomResponse::from)
                .orElseThrow(() -> new RoomNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<RoomResponse> list(Long hotelId, Boolean available, Pageable pageable) {
        return rooms.findCatalog(hotelId, available, pageable).map(RoomResponse::from);
    }

    @Transactional
    public RoomResponse update(long id, PatchRoomRequest request) {
        Room room = findForUpdate(id);
        String number = Objects.requireNonNullElse(request.number(), room.getNumber()).strip();
        if (!number.equals(room.getNumber())) {
            requireFreeNumber(room.getHotel().getId(), number);
        }
        boolean available = Objects.requireNonNullElse(request.available(), room.isAvailable());
        if (room.isAvailable() && !available) {
            requireNoCurrentStays(id);
        }
        room.updateDetails(number, available);
        return RoomResponse.from(room);
    }

    @Transactional
    public void delete(long id) {
        Room room = findForUpdate(id);
        if (allocations.existsByRoomId(id)) {
            throw new RoomInUseException(
                    "ROOM_HAS_ALLOCATION_HISTORY",
                    "A room with allocation history cannot be deleted; set available=false");
        }
        rooms.delete(room);
    }

    private void requireNoCurrentStays(long id) {
        if (allocations.existsByRoomIdAndStateAndEndDateAfter(
                id, AllocationState.ACTIVE, dates.today())) {
            throw new RoomInUseException(
                    "ROOM_HAS_ACTIVE_ALLOCATIONS",
                    "A room with current or future stays cannot be taken out of service");
        }
    }

    private Room findForUpdate(long id) {
        long hotelId =
                rooms.findHotelIdByRoomId(id).orElseThrow(() -> new RoomNotFoundException(id));
        hotels.findForUpdateById(hotelId).orElseThrow(() -> new RoomNotFoundException(id));
        return rooms.findForUpdateById(id).orElseThrow(() -> new RoomNotFoundException(id));
    }

    private void requireFreeNumber(long hotelId, String number) {
        if (rooms.existsByHotelIdAndNumber(hotelId, number)) {
            throw new RoomNumberConflictException(hotelId);
        }
    }
}
