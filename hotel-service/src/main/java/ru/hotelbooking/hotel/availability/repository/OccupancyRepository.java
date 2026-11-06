package ru.hotelbooking.hotel.availability.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import ru.hotelbooking.hotel.catalog.entity.Hotel;

import java.time.LocalDate;
import java.util.List;

public interface OccupancyRepository extends Repository<Hotel, Long> {
    @Query(
            value =
                    """
                    select h.id as hotelId, h.name as hotelName,
                           r.id as roomId, r.number as number, r.available as available,
                           r.times_booked as timesBooked, count(a.id) as allocationCount,
                           sum(case when a.id is null then 0
                               else datediff('DAY', greatest(a.start_date, :startDate),
                                                   least(a.end_date, :endDate)) end)
                               as occupiedRoomNights
                    from hotels h
                    left join rooms r on r.hotel_id = h.id
                         and (:available is null or r.available = :available)
                    left join room_allocations a on a.room_id = r.id and a.state = 'ACTIVE'
                         and a.start_date < :endDate and a.end_date > :startDate
                    where (:hotelId is null or h.id = :hotelId)
                      and (:available is null or r.id is not null)
                    group by h.id, h.name, r.id, r.number, r.available, r.times_booked
                    """,
            nativeQuery = true)
    List<RoomReportRow> report(
            @Param("hotelId") Long hotelId,
            @Param("available") Boolean available,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    interface RoomReportRow {
        long getHotelId();

        String getHotelName();

        Long getRoomId();

        String getNumber();

        Boolean getAvailable();

        Long getTimesBooked();

        long getAllocationCount();

        long getOccupiedRoomNights();
    }
}
