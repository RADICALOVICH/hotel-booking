package ru.hotelbooking.hotel.catalog;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    boolean existsByHotelIdAndNumber(long hotelId, String number);

    @Query("select r.hotel.id from Room r where r.id = :id")
    Optional<Long> findHotelIdByRoomId(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Room> findForUpdateById(long id);

    @Query(
            """
            select r from Room r
            where (:hotelId is null or r.hotel.id = :hotelId)
              and (:available is null or r.available = :available)
            """)
    Page<Room> findCatalog(
            @Param("hotelId") Long hotelId,
            @Param("available") Boolean available,
            Pageable pageable);
}
