package ru.hotelbooking.hotel.catalog;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        if (rooms.existsByHotelIdAndNumber(hotelId, number)) {
            throw new RoomNumberConflictException(hotelId);
        }
        return toResponse(rooms.save(new Room(hotel, number)));
    }

    @Transactional(readOnly = true)
    public RoomResponse get(long id) {
        return rooms.findById(id)
                .map(RoomService::toResponse)
                .orElseThrow(() -> new RoomNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list() {
        return rooms.findAll(Sort.by("id")).stream().map(RoomService::toResponse).toList();
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
