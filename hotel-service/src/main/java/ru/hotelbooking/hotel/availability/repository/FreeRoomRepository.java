package ru.hotelbooking.hotel.availability.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import ru.hotelbooking.hotel.availability.dto.RoomStatisticsResponse;
import ru.hotelbooking.hotel.catalog.entity.Room;

import java.time.LocalDate;

/** Read side of the calendar: one filter for free rooms, ordered by the caller's Pageable. */
public interface FreeRoomRepository extends Repository<Room, Long> {
    @Query(
            """
            select r from Room r
            where r.available = true
              and (:hotelId is null or r.hotel.id = :hotelId)
              and not exists (
                select a.id from RoomAllocation a
                where a.room = r
                  and a.state = ru.hotelbooking.hotel.availability.entity.AllocationState.ACTIVE
                  and a.startDate < :endDate
                  and a.endDate > :startDate)
            """)
    Page<Room> findFree(
            @Param("hotelId") Long hotelId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query(
            value =
                    """
                    select new ru.hotelbooking.hotel.availability.dto.RoomStatisticsResponse(
                      r.id, r.hotel.id, r.number, r.available, r.timesBooked,
                      (select count(a.id) from RoomAllocation a
                       where a.room = r
                         and a.state = ru.hotelbooking.hotel.availability.entity.AllocationState.ACTIVE
                         and a.endDate > :today))
                    from Room r
                    where (:hotelId is null or r.hotel.id = :hotelId)
                    """,
            countQuery =
                    "select count(r) from Room r where (:hotelId is null or r.hotel.id = :hotelId)")
    Page<RoomStatisticsResponse> statistics(
            @Param("hotelId") Long hotelId, @Param("today") LocalDate today, Pageable pageable);
}
