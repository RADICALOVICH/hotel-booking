package ru.hotelbooking.hotel.catalog.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import ru.hotelbooking.hotel.catalog.entity.Hotel;

import java.util.Optional;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
    Page<Hotel> findByNameContainingIgnoreCaseAndAddressContainingIgnoreCase(
            String name, String address, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Hotel> findForUpdateById(long id);
}
