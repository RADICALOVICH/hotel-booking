package ru.hotelbooking.hotel.availability.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import ru.hotelbooking.hotel.catalog.entity.Hotel;

import java.util.List;

public interface OccupancyRepository extends Repository<Hotel, Long> {
    @Query(
            value =
                    """
                    select h.id as hotelId, h.name as hotelName,
                           r.id as roomId, r.number as number, r.available as available,
                           r.times_booked as timesBooked
                    from hotels h
                    left join rooms r on r.hotel_id = h.id
                         and (:available is null or r.available = :available)
                    where (:hotelId is null or h.id = :hotelId)
                      and (:available is null or r.id is not null)
                    """,
            nativeQuery = true)
    List<RoomReportRow> report(
            @Param("hotelId") Long hotelId, @Param("available") Boolean available);

    interface RoomReportRow {
        long getHotelId();

        String getHotelName();

        Long getRoomId();

        String getNumber();

        Boolean getAvailable();

        Long getTimesBooked();
    }
}
