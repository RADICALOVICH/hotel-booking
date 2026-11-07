package ru.hotelbooking.hotel.availability.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import ru.hotelbooking.hotel.catalog.dto.RoomResponse;
import ru.hotelbooking.hotel.catalog.entity.Room;

import java.time.LocalDate;
import java.util.Collection;

/** Read side of the calendar: one filter for free rooms, ordered by the caller's Pageable. */
public interface FreeRoomRepository extends Repository<Room, Long> {
    @Query(
            """
            select new ru.hotelbooking.hotel.catalog.dto.RoomResponse(
                r.id, r.hotel.id, r.number, r.available, r.timesBooked) from Room r
            where r.available = true
              and r.id not in :occupiedRoomIds
              and (:hotelId is null or r.hotel.id = :hotelId)
              and not exists (
                select a.id from RoomAllocation a
                where a.room = r
                  and a.state = ru.hotelbooking.hotel.availability.entity.AllocationState.ACTIVE
                  and a.startDate < :endDate
                  and a.endDate > :startDate)
            """)
    Page<RoomResponse> findFree(
            @Param("occupiedRoomIds") Collection<Long> occupiedRoomIds,
            @Param("hotelId") Long hotelId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);
}
