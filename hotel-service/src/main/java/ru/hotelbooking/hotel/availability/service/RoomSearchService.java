package ru.hotelbooking.hotel.availability.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hotelbooking.hotel.availability.dto.RoomStatisticsResponse;
import ru.hotelbooking.hotel.availability.repository.FreeRoomRepository;
import ru.hotelbooking.hotel.catalog.dto.RoomResponse;

import java.time.LocalDate;

@Service
public class RoomSearchService {
    public static final Sort BY_ID = Sort.by("id");

    /** Least used rooms first, so automatic selection spreads stays evenly. */
    public static final Sort RECOMMENDED =
            Sort.by(Sort.Order.asc("timesBooked"), Sort.Order.asc("id"));

    private final FreeRoomRepository freeRooms;
    private final StayDates dates;

    RoomSearchService(FreeRoomRepository freeRooms, StayDates dates) {
        this.freeRooms = freeRooms;
        this.dates = dates;
    }

    @Transactional(readOnly = true)
    public Page<RoomResponse> free(
            Long hotelId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        dates.requireBookable(startDate, endDate);
        return freeRooms.findFree(hotelId, startDate, endDate, pageable).map(RoomResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<RoomStatisticsResponse> statistics(Long hotelId, Pageable pageable) {
        return freeRooms.statistics(hotelId, dates.today(), pageable);
    }
}
