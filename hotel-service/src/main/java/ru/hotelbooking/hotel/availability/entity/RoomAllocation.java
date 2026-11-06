package ru.hotelbooking.hotel.availability.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Check;

import ru.hotelbooking.hotel.catalog.entity.Room;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "room_allocations",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_room_allocations_allocation_id",
                        columnNames = "allocation_id"),
        indexes =
                @Index(
                        name = "ix_room_allocations_room_state_dates",
                        columnList = "room_id, state, start_date, end_date"))
@Check(name = "ck_room_allocations_dates", constraints = "end_date > start_date")
public class RoomAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allocation_id", nullable = false, length = 100, updatable = false)
    private String allocationId;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private long bookingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "room_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_room_allocations_room"))
    private Room room;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AllocationState state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected RoomAllocation() {}

    public RoomAllocation(
            Room room,
            String allocationId,
            long bookingId,
            LocalDate startDate,
            LocalDate endDate,
            AllocationState state,
            Instant now) {
        this.room = room;
        this.allocationId = allocationId;
        this.bookingId = bookingId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.state = state;
        this.createdAt = now;
        this.releasedAt = state == AllocationState.RELEASED ? now : null;
    }

    public boolean describes(long roomId, long bookingId, LocalDate startDate, LocalDate endDate) {
        return room.getId() == roomId
                && this.bookingId == bookingId
                && this.startDate.equals(startDate)
                && this.endDate.equals(endDate);
    }

    public void release(Instant now) {
        state = AllocationState.RELEASED;
        releasedAt = now;
    }

    public String getAllocationId() {
        return allocationId;
    }

    public long getBookingId() {
        return bookingId;
    }

    public Room getRoom() {
        return room;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public AllocationState getState() {
        return state;
    }
}
