package ru.hotelbooking.hotel.availability.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.hotelbooking.hotel.availability.entity.AllocationState;
import ru.hotelbooking.hotel.availability.entity.RoomAllocation;

import java.time.LocalDate;
import java.util.Optional;

public interface RoomAllocationRepository extends JpaRepository<RoomAllocation, Long> {
    Optional<RoomAllocation> findByAllocationId(String allocationId);

    boolean existsByRoomId(long roomId);

    boolean existsByRoomIdAndStateAndEndDateAfter(
            long roomId, AllocationState state, LocalDate date);

    /** Half-open stays [start, end) overlap when each one starts before the other ends. */
    @Query(
            """
            select count(a) > 0 from RoomAllocation a
            where a.room.id = :roomId
              and a.state = ru.hotelbooking.hotel.availability.entity.AllocationState.ACTIVE
              and a.startDate < :endDate
              and a.endDate > :startDate
            """)
    boolean existsActiveOverlap(
            @Param("roomId") long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
