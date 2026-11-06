package ru.hotelbooking.booking.booking.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import ru.hotelbooking.booking.booking.entity.Booking;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Page<Booking> findByUserId(long userId, Pageable pageable);

    Optional<Booking> findByIdAndUserId(long id, long userId);

    boolean existsByUserId(long userId);
}
