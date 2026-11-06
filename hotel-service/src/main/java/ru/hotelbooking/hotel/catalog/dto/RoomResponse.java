package ru.hotelbooking.hotel.catalog.dto;

import ru.hotelbooking.hotel.catalog.entity.Room;

public record RoomResponse(
        long id, long hotelId, String number, boolean available, long timesBooked) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getHotel().getId(),
                room.getNumber(),
                room.isAvailable(),
                room.getTimesBooked());
    }
}
