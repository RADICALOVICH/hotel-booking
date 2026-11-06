package ru.hotelbooking.booking.booking.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import ru.hotelbooking.booking.booking.entity.Booking;
import ru.hotelbooking.booking.booking.entity.BookingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Page<Booking> findByUserId(long userId, Pageable pageable);

    Optional<Booking> findByIdAndUserId(long id, long userId);

    Optional<Booking> findByUserIdAndRequestId(long userId, UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id and b.user.id = :userId")
    Optional<Booking> lockByIdAndUserId(long id, long userId);

    @Query(
            "select b.id from Booking b where b.status = :status and b.updatedAt <= :cutoff order by b.updatedAt, b.id")
    List<Long> findStaleIds(BookingStatus status, Instant cutoff, Pageable pageable);

    @Query(
            "select b.id from Booking b where b.compensationPending = true and b.nextCompensationAt <= :now order by b.nextCompensationAt, b.id")
    List<Long> findDueCompensationIds(Instant now, Pageable pageable);

    boolean existsByUserId(long userId);
}
