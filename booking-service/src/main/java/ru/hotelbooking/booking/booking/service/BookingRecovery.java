package ru.hotelbooking.booking.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import ru.hotelbooking.booking.booking.client.HotelClient;
import ru.hotelbooking.booking.booking.client.HotelClientException;
import ru.hotelbooking.booking.booking.dto.BookingResponse;
import ru.hotelbooking.booking.common.web.TraceIds;

@Service
public class BookingRecovery {
    private static final Logger log = LoggerFactory.getLogger(BookingRecovery.class);

    private final BookingTransactions bookings;
    private final HotelClient hotel;

    BookingRecovery(BookingTransactions bookings, HotelClient hotel) {
        this.bookings = bookings;
        this.hotel = hotel;
    }

    @Scheduled(
            fixedDelayString = "${booking.compensation.poll-delay-ms:1000}",
            initialDelayString = "${booking.compensation.poll-delay-ms:1000}")
    public void run() {
        for (long id : bookings.stalePendingIds()) {
            bookings.cancelStale(id)
                    .ifPresent(
                            traceId -> {
                                try (var scope = TraceIds.open(traceId)) {
                                    log.warn("event=booking_stale_cancelled bookingId={}", id);
                                }
                            });
        }
        for (long id : bookings.dueCompensationIds()) {
            compensate(id);
        }
    }

    public BookingResponse restart(long id) {
        bookings.restartCompensation(id);
        log.info("event=compensation_restarted bookingId={}", id);
        compensate(id);
        return bookings.get(id);
    }

    void compensate(long id) {
        bookings.claimCompensation(id).ifPresent(claim -> processClaim(id, claim));
    }

    private void processClaim(long id, BookingTransactions.CompensationClaim claim) {
        try (var scope = TraceIds.open(TraceIds.fromHeader(claim.traceId()))) {
            if (claim.allocation() == null) {
                exhausted(id, claim.attempt());
                return;
            }
            release(id, claim);
        }
    }

    private void release(long id, BookingTransactions.CompensationClaim claim) {
        BookingAllocation allocation = claim.allocation();
        log.info(
                "event=compensation_started bookingId={} roomId={} allocationId={} attempt={}",
                id,
                allocation.roomId(),
                allocation.request().allocationId(),
                claim.attempt());
        try {
            if (claim.attempt() == 1) {
                hotel.release(allocation.roomId(), allocation.request());
            } else {
                hotel.releaseOnce(allocation.roomId(), allocation.request());
            }
        } catch (HotelClientException failure) {
            // Hotel cannot delete a room that has allocation history.
            if (failure.status() != 404 || !failure.code().equals("ROOM_NOT_FOUND")) {
                recordReleaseFailure(id, claim, failure);
                return;
            }
        }
        if (bookings.finishCompensation(id, claim, true)) {
            log.info(
                    "event=compensation_completed bookingId={} roomId={} allocationId={} attempt={}",
                    id,
                    allocation.roomId(),
                    allocation.request().allocationId(),
                    claim.attempt());
        }
    }

    private void recordReleaseFailure(
            long id, BookingTransactions.CompensationClaim claim, HotelClientException failure) {
        if (bookings.finishCompensation(id, claim, false)) {
            log.warn(
                    "event=compensation_release_failed bookingId={} attempt={} code={}",
                    id,
                    claim.attempt(),
                    failure.code());
            if (claim.attempt() == BookingTransactions.MAX_COMPENSATION_ATTEMPTS) {
                exhausted(id, claim.attempt());
            }
        }
    }

    private static void exhausted(long id, int attempt) {
        log.error("event=compensation_exhausted bookingId={} attempts={}", id, attempt);
    }
}
