package ru.hotelbooking.hotel.availability.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import ru.hotelbooking.hotel.availability.client.BookingOccupancy;
import ru.hotelbooking.hotel.availability.dto.RoomStatisticsResponse;
import ru.hotelbooking.hotel.availability.repository.FreeRoomRepository;
import ru.hotelbooking.hotel.catalog.dto.RoomResponse;
import ru.hotelbooking.hotel.catalog.service.RoomService;
import ru.hotelbooking.support.dates.BookedStay;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomSearchService {
    public static final Sort BY_ID = Sort.by("id");

    /** Least used rooms first, so automatic selection spreads stays evenly. */
    public static final Sort RECOMMENDED =
            Sort.by(Sort.Order.asc("timesBooked"), Sort.Order.asc("id"));

    private final FreeRoomRepository freeRooms;
    private final StayDates dates;
    private final BookingOccupancy occupancy;
    private final RoomService rooms;

    RoomSearchService(
            FreeRoomRepository freeRooms,
            StayDates dates,
            BookingOccupancy occupancy,
            RoomService rooms) {
        this.freeRooms = freeRooms;
        this.dates = dates;
        this.occupancy = occupancy;
        this.rooms = rooms;
    }

    public Page<RoomResponse> free(
            Long hotelId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        dates.requireBookable(startDate, endDate);
        List<Long> occupied =
                occupancy.find(startDate, endDate).stream()
                        .map(BookedStay::roomId)
                        .distinct()
                        .toList();
        return freeRooms.findFree(
                occupied.isEmpty() ? List.of(-1L) : occupied,
                hotelId,
                startDate,
                endDate,
                pageable);
    }

    public Page<RoomStatisticsResponse> statistics(Long hotelId, Pageable pageable) {
        var counts =
                occupancy.find(dates.today(), LocalDate.of(9999, 12, 31)).stream()
                        .collect(Collectors.groupingBy(BookedStay::roomId, Collectors.counting()));
        return rooms.list(hotelId, null, pageable)
                .map(
                        room ->
                                new RoomStatisticsResponse(
                                        room.id(),
                                        room.hotelId(),
                                        room.number(),
                                        room.available(),
                                        room.timesBooked(),
                                        counts.getOrDefault(room.id(), 0L)));
    }
}
