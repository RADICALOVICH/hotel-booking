package ru.hotelbooking.hotel.catalog.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hotelbooking.hotel.catalog.dto.CreateRoomRequest;
import ru.hotelbooking.hotel.catalog.dto.PatchRoomRequest;
import ru.hotelbooking.hotel.catalog.dto.RoomResponse;
import ru.hotelbooking.hotel.catalog.entity.Hotel;
import ru.hotelbooking.hotel.catalog.entity.Room;
import ru.hotelbooking.hotel.catalog.exception.HotelNotFoundException;
import ru.hotelbooking.hotel.catalog.exception.RoomNotFoundException;
import ru.hotelbooking.hotel.catalog.exception.RoomNumberConflictException;
import ru.hotelbooking.hotel.catalog.repository.HotelRepository;
import ru.hotelbooking.hotel.catalog.repository.RoomRepository;

import java.util.Objects;

@Service
public class RoomService {
    private final RoomRepository rooms;
    private final HotelRepository hotels;

    public RoomService(RoomRepository rooms, HotelRepository hotels) {
        this.rooms = rooms;
        this.hotels = hotels;
    }

    @Transactional
    public RoomResponse create(CreateRoomRequest request) {
        long hotelId = request.hotelId();
        Hotel hotel =
                hotels.findForUpdateById(hotelId)
                        .orElseThrow(() -> new HotelNotFoundException(hotelId));
        String number = request.number().strip();
        requireFreeNumber(hotelId, number);
        return toResponse(rooms.save(new Room(hotel, number)));
    }

    @Transactional(readOnly = true)
    public RoomResponse get(long id) {
        return rooms.findById(id)
                .map(RoomService::toResponse)
                .orElseThrow(() -> new RoomNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<RoomResponse> list(Long hotelId, Boolean available, Pageable pageable) {
        return rooms.findCatalog(hotelId, available, pageable).map(RoomService::toResponse);
    }

    @Transactional
    public RoomResponse update(long id, PatchRoomRequest request) {
        Room room = findForUpdate(id);
        String number = Objects.requireNonNullElse(request.number(), room.getNumber()).strip();
        if (!number.equals(room.getNumber())) {
            requireFreeNumber(room.getHotel().getId(), number);
        }
        room.updateDetails(
                number, Objects.requireNonNullElse(request.available(), room.isAvailable()));
        return toResponse(room);
    }

    @Transactional
    public void delete(long id) {
        rooms.delete(findForUpdate(id));
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

    private static RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getHotel().getId(),
                room.getNumber(),
                room.isAvailable(),
                room.getTimesBooked());
    }
}
