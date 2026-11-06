package ru.hotelbooking.hotel.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
    boolean existsByHotelIdAndNumber(long hotelId, String number);
}
