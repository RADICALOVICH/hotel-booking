package ru.hotelbooking.booking.booking.entity;

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
import jakarta.persistence.Version;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

import ru.hotelbooking.booking.booking.dto.CreateBookingRequest;
import ru.hotelbooking.booking.user.entity.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "bookings",
        indexes = {
            @Index(name = "ix_bookings_user_history", columnList = "user_id, created_at, id"),
            @Index(name = "ix_bookings_pending", columnList = "status, updated_at"),
            @Index(
                    name = "ix_bookings_compensation",
                    columnList = "compensation_pending, next_compensation_at")
        },
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_bookings_user_request",
                        columnNames = {"user_id", "request_id"}))
@Check(name = "ck_bookings_dates", constraints = "end_date > start_date")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bookings_user"))
    private User user;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "auto_select", nullable = false)
    @ColumnDefault("false")
    private boolean autoSelect;

    @Column(name = "requested_room_id")
    private Long requestedRoomId;

    @Column(name = "hotel_id")
    private Long hotelId;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private long version;

    @Column(name = "compensation_pending", nullable = false)
    @ColumnDefault("false")
    private boolean compensationPending;

    @Column(name = "compensation_attempts", nullable = false)
    @ColumnDefault("0")
    private int compensationAttempts;

    @Column(name = "next_compensation_at")
    private Instant nextCompensationAt;

    @Column(name = "recovery_trace_id", length = 32)
    private String recoveryTraceId;

    protected Booking() {}

    public Booking(User user, CreateBookingRequest request, Instant now, String traceId) {
        this.user = user;
        requestId = request.requestId();
        autoSelect = request.autoSelect();
        requestedRoomId = request.roomId();
        hotelId = request.hotelId();
        startDate = request.startDate();
        endDate = request.endDate();
        status = BookingStatus.PENDING;
        createdAt = now;
        updatedAt = now;
        recoveryTraceId = traceId;
    }

    public boolean matches(CreateBookingRequest request) {
        return Objects.equals(autoSelect, request.autoSelect())
                && Objects.equals(requestedRoomId, request.roomId())
                && Objects.equals(hotelId, request.hotelId())
                && startDate.equals(request.startDate())
                && endDate.equals(request.endDate());
    }

    public void selectRoom(long candidateId, Instant now) {
        roomId = candidateId;
        updatedAt = now;
    }

    public void confirm(Instant now) {
        status = BookingStatus.CONFIRMED;
        updatedAt = now;
    }

    public void cancel(boolean releaseRoom, Instant now, String traceId) {
        status = BookingStatus.CANCELLED;
        updatedAt = now;
        recoveryTraceId = traceId;
        if (releaseRoom && roomId != null) {
            compensationPending = true;
            nextCompensationAt = now;
        }
    }

    public void claimCompensation(Instant now, Instant leaseUntil) {
        compensationAttempts++;
        scheduleCompensation(now, leaseUntil);
    }

    public void scheduleCompensation(Instant now, Instant nextAttempt) {
        nextCompensationAt = nextAttempt;
        updatedAt = now;
    }

    public void completeCompensation(Instant now) {
        compensationPending = false;
        scheduleCompensation(now, null);
    }

    public void restartCompensation(Instant now, String traceId) {
        compensationAttempts = 0;
        recoveryTraceId = traceId;
        scheduleCompensation(now, now);
    }

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean isCompensationPending() {
        return compensationPending;
    }

    public int getCompensationAttempts() {
        return compensationAttempts;
    }

    public Instant getNextCompensationAt() {
        return nextCompensationAt;
    }

    public String getRecoveryTraceId() {
        return recoveryTraceId;
    }
}
