package ru.hotelbooking.booking.booking.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hotelbooking.booking.booking.dto.BookingResponse;
import ru.hotelbooking.booking.booking.dto.CreateBookingRequest;
import ru.hotelbooking.booking.booking.entity.Booking;
import ru.hotelbooking.booking.booking.entity.BookingStatus;
import ru.hotelbooking.booking.booking.exception.BookingNotFoundException;
import ru.hotelbooking.booking.booking.exception.BookingOperationException;
import ru.hotelbooking.booking.booking.repository.BookingRepository;
import ru.hotelbooking.booking.user.exception.UserNotFoundException;
import ru.hotelbooking.booking.user.repository.UserRepository;
import ru.hotelbooking.support.trace.TraceIds;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
class BookingTransactions {
    static final int MAX_COMPENSATION_ATTEMPTS = 10;
    private static final Duration STALE_PENDING_AGE = Duration.ofMinutes(2);
    private static final Duration COMPENSATION_LEASE = Duration.ofSeconds(30);
    private static final PageRequest RECOVERY_BATCH = PageRequest.of(0, 20);

    private final BookingRepository bookings;
    private final UserRepository users;
    private final Clock clock;
    private final StayDates dates;

    BookingTransactions(
            BookingRepository bookings,
            UserRepository users,
            @Qualifier("jwtClock") Clock clock,
            StayDates dates) {
        this.bookings = bookings;
        this.users = users;
        this.clock = clock;
        this.dates = dates;
    }

    @Transactional(readOnly = true)
    public Optional<BookingResponse> replay(long userId, CreateBookingRequest request) {
        return bookings.findByUserIdAndRequestId(userId, request.requestId())
                .map(
                        booking -> {
                            if (!booking.matches(request)) {
                                throw new BookingOperationException(
                                        409,
                                        "REQUEST_ID_CONFLICT",
                                        "requestId is already used with other booking details",
                                        booking.getId());
                            }
                            return BookingResponse.from(booking);
                        });
    }

    @Transactional
    public BookingResponse insert(long userId, CreateBookingRequest request) {
        var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        return BookingResponse.from(
                bookings.saveAndFlush(
                        new Booking(user, request, clock.instant(), TraceIds.current())));
    }

    @Transactional(readOnly = true)
    public boolean userExists(long userId) {
        return users.existsById(userId);
    }

    @Transactional
    public Optional<BookingResponse> selectRoom(long id, long roomId) {
        Booking booking = lock(id);
        if (booking.getStatus() != BookingStatus.PENDING) {
            return Optional.empty();
        }
        booking.selectRoom(roomId, clock.instant());
        return Optional.of(BookingResponse.from(booking));
    }

    @Transactional
    public BookingResponse confirm(long id) {
        Booking booking = lock(id);
        if (booking.getStatus() == BookingStatus.PENDING) {
            booking.confirm(clock.instant());
        }
        return BookingResponse.from(booking);
    }

    @Transactional
    public Cancellation cancelPending(long id, boolean releaseRoom) {
        Booking booking = lock(id);
        boolean changed = booking.getStatus() == BookingStatus.PENDING;
        if (changed) {
            booking.cancel(releaseRoom, clock.instant(), TraceIds.current());
        }
        return new Cancellation(BookingResponse.from(booking), changed);
    }

    @Transactional
    public Cancellation cancelOwned(long id, long userId) {
        Booking booking =
                bookings.lockByIdAndUserId(id, userId)
                        .orElseThrow(() -> new BookingNotFoundException(id));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return new Cancellation(BookingResponse.from(booking), false);
        }
        if (!dates.today().isBefore(booking.getStartDate())) {
            throw new BookingOperationException(
                    409,
                    "CANCELLATION_TOO_LATE",
                    "Cancellation is allowed only before check-in day",
                    id);
        }
        booking.cancel(true, clock.instant(), TraceIds.current());
        return new Cancellation(BookingResponse.from(booking), true);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(long id) {
        return bookings.findById(id)
                .map(BookingResponse::from)
                .orElseThrow(() -> new BookingNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Long> stalePendingIds() {
        return bookings.findStaleIds(
                BookingStatus.PENDING, clock.instant().minus(STALE_PENDING_AGE), RECOVERY_BATCH);
    }

    @Transactional
    public Optional<String> cancelStale(long id) {
        Booking booking = lock(id);
        Instant now = clock.instant();
        if (booking.getStatus() != BookingStatus.PENDING
                || booking.getUpdatedAt() == null
                || booking.getUpdatedAt().isAfter(now.minus(STALE_PENDING_AGE))) {
            return Optional.empty();
        }
        String traceId = TraceIds.fromHeader(booking.getRecoveryTraceId());
        booking.cancel(true, now, traceId);
        return Optional.of(traceId);
    }

    @Transactional(readOnly = true)
    public List<Long> dueCompensationIds() {
        return bookings.findDueCompensationIds(clock.instant(), RECOVERY_BATCH);
    }

    @Transactional
    public Optional<CompensationClaim> claimCompensation(long id) {
        Booking booking = lock(id);
        Instant now = clock.instant();
        Instant next = booking.getNextCompensationAt();
        if (!booking.isCompensationPending() || next == null || next.isAfter(now)) {
            return Optional.empty();
        }
        if (booking.getCompensationAttempts() >= MAX_COMPENSATION_ATTEMPTS) {
            booking.scheduleCompensation(now, null);
            return Optional.of(
                    new CompensationClaim(
                            null,
                            booking.getVersion(),
                            booking.getCompensationAttempts(),
                            booking.getRecoveryTraceId()));
        }
        booking.claimCompensation(now, now.plus(COMPENSATION_LEASE));
        bookings.flush();
        return Optional.of(
                new CompensationClaim(
                        BookingAllocation.from(BookingResponse.from(booking)),
                        booking.getVersion(),
                        booking.getCompensationAttempts(),
                        booking.getRecoveryTraceId()));
    }

    @Transactional
    public boolean finishCompensation(long id, CompensationClaim claim, boolean released) {
        Booking booking = lock(id);
        if (booking.getVersion() != claim.version()) {
            return false;
        }
        Instant now = clock.instant();
        if (released) {
            booking.completeCompensation(now);
        } else {
            Instant next =
                    claim.attempt() < MAX_COMPENSATION_ATTEMPTS
                            ? now.plusSeconds(Math.min(30, 1L << (claim.attempt() - 1)))
                            : null;
            booking.scheduleCompensation(now, next);
        }
        return true;
    }

    @Transactional
    public void restartCompensation(long id) {
        Booking booking = lock(id);
        if (!booking.isCompensationPending() || booking.getNextCompensationAt() != null) {
            throw new BookingOperationException(
                    409,
                    "COMPENSATION_NOT_PENDING",
                    "Booking does not have an exhausted compensation to restart",
                    id);
        }
        booking.restartCompensation(clock.instant(), TraceIds.current());
    }

    private Booking lock(long id) {
        return bookings.lockById(id).orElseThrow(() -> new BookingNotFoundException(id));
    }

    record Cancellation(BookingResponse booking, boolean changed) {}

    record CompensationClaim(
            BookingAllocation allocation, long version, int attempt, String traceId) {}
}
