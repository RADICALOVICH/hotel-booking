package ru.hotelbooking.hotel.catalog.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import ru.hotelbooking.hotel.availability.client.BookingOccupancy;
import ru.hotelbooking.hotel.availability.service.StayDates;
import ru.hotelbooking.hotel.catalog.dto.CreateRoomRequest;
import ru.hotelbooking.hotel.catalog.dto.PatchRoomRequest;
import ru.hotelbooking.hotel.catalog.dto.RoomResponse;
import ru.hotelbooking.hotel.catalog.exception.RoomInUseException;

import java.time.LocalDate;

/** Remote reads precede local transactions; room locks still protect concurrent catalog changes. */
@Service
public class RoomService {
    private static final LocalDate LAST_DATE = LocalDate.of(9999, 12, 31);
    private final RoomTransactions rooms;
    private final BookingOccupancy occupancy;
    private final StayDates dates;

    RoomService(RoomTransactions rooms, BookingOccupancy occupancy, StayDates dates) {
        this.rooms = rooms;
        this.occupancy = occupancy;
        this.dates = dates;
    }

    public RoomResponse create(CreateRoomRequest request) {
        return rooms.create(request);
    }

    public RoomResponse get(long id) {
        return rooms.get(id);
    }

    public Page<RoomResponse> list(Long hotelId, Boolean available, Pageable pageable) {
        return rooms.list(hotelId, available, pageable);
    }

    public RoomResponse update(long id, PatchRoomRequest request) {
        if (Boolean.FALSE.equals(request.available())
                && rooms.get(id).available()
                && hasBookings(id, dates.today())) {
            throw new RoomInUseException(
                    "ROOM_HAS_ACTIVE_ALLOCATIONS",
                    "A room with current or future bookings cannot be taken out of service");
        }
        return rooms.update(id, request);
    }

    public void delete(long id) {
        rooms.get(id);
        if (hasBookings(id, LocalDate.of(1, 1, 1))) {
            throw new RoomInUseException(
                    "ROOM_HAS_ALLOCATION_HISTORY",
                    "A room with booking history cannot be deleted; set available=false");
        }
        rooms.delete(id);
    }

    private boolean hasBookings(long id, LocalDate start) {
        return occupancy.find(start, LAST_DATE).stream().anyMatch(stay -> stay.roomId() == id);
    }
}
