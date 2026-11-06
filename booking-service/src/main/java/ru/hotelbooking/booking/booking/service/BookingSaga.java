package ru.hotelbooking.booking.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import ru.hotelbooking.booking.booking.client.HotelClient;
import ru.hotelbooking.booking.booking.client.HotelClientException;
import ru.hotelbooking.booking.booking.client.RecommendedRoom;
import ru.hotelbooking.booking.booking.client.RecommendedRooms;
import ru.hotelbooking.booking.booking.dto.BookingResponse;
import ru.hotelbooking.booking.booking.dto.CreateBookingRequest;
import ru.hotelbooking.booking.booking.entity.BookingStatus;
import ru.hotelbooking.booking.booking.exception.BookingInputException;
import ru.hotelbooking.booking.booking.exception.BookingOperationException;
import ru.hotelbooking.booking.user.exception.UserNotFoundException;

import java.time.LocalDate;
import java.util.List;

@Service
public class BookingSaga {
    private static final Logger log = LoggerFactory.getLogger(BookingSaga.class);
    private static final int MAX_CANDIDATES = 10;

    private final BookingTransactions bookings;
    private final BookingRecovery recovery;
    private final HotelClient hotel;
    private final StayDates dates;

    BookingSaga(
            BookingTransactions bookings,
            BookingRecovery recovery,
            HotelClient hotel,
            StayDates dates) {
        this.bookings = bookings;
        this.recovery = recovery;
        this.hotel = hotel;
        this.dates = dates;
    }

    public Creation create(long userId, CreateBookingRequest request, String userToken) {
        if (request.requestId() == null) {
            throw new BookingInputException("requestId", "Request id is required");
        }
        var existing = bookings.replay(userId, request);
        if (existing.isPresent()) {
            return new Creation(existing.get(), false);
        }
        validateNew(request);

        BookingResponse pending;
        try {
            pending = bookings.insert(userId, request);
        } catch (DataIntegrityViolationException failure) {
            // Reread only after Spring rolls back the losing insert transaction.
            existing = bookings.replay(userId, request);
            if (existing.isPresent()) {
                return new Creation(existing.get(), false);
            }
            if (!bookings.userExists(userId)) {
                throw new UserNotFoundException(userId);
            }
            throw failure;
        }

        log.info("event=booking_pending bookingId={} userId={}", pending.id(), userId);
        return new Creation(coordinate(pending.id(), request, userToken), true);
    }

    public RecommendedRooms recommend(
            LocalDate start, LocalDate end, Long hotelId, int page, int size, String userToken) {
        dates.requireBookable(start, end);
        try {
            return hotel.recommend(start, end, hotelId, page, size, userToken);
        } catch (HotelClientException failure) {
            throw remoteError(failure, null);
        }
    }

    public BookingResponse cancel(long userId, long id) {
        var cancellation = bookings.cancelOwned(id, userId);
        if (cancellation.changed()) {
            log.info("event=booking_cancelled bookingId={} reason=owner", id);
            if (cancellation.booking().compensationPending()) {
                recovery.compensate(id);
                return bookings.get(id);
            }
        }
        return cancellation.booking();
    }

    private BookingResponse coordinate(long id, CreateBookingRequest request, String userToken) {
        List<Long> candidates = candidates(id, request, userToken);
        for (long roomId : candidates) {
            var pending = bookings.selectRoom(id, roomId).orElseThrow(() -> cancelled(id));
            BookingAllocation allocation = BookingAllocation.from(pending);
            log.info(
                    "event=booking_confirm_requested bookingId={} roomId={} allocationId={}",
                    id,
                    roomId,
                    allocation.request().allocationId());
            try {
                hotel.confirm(roomId, allocation.request());
            } catch (HotelClientException failure) {
                if (request.autoSelect() && failure.code().equals("ROOM_UNAVAILABLE")) {
                    log.info(
                            "event=booking_candidate_unavailable bookingId={} roomId={}",
                            id,
                            roomId);
                    continue;
                }
                throw fail(id, failure.ambiguous(), confirmError(failure, id));
            }
            return finish(id);
        }
        throw fail(
                id,
                false,
                new BookingOperationException(
                        409, "NO_ROOMS_AVAILABLE", "No recommended rooms are available", id));
    }

    private List<Long> candidates(long id, CreateBookingRequest request, String userToken) {
        if (!request.autoSelect()) {
            return List.of(request.roomId());
        }
        try {
            return hotel
                    .recommend(
                            request.startDate(),
                            request.endDate(),
                            request.hotelId(),
                            0,
                            MAX_CANDIDATES,
                            userToken)
                    .rooms()
                    .stream()
                    .map(RecommendedRoom::id)
                    .toList();
        } catch (HotelClientException failure) {
            throw fail(id, false, remoteError(failure, id));
        }
    }

    private BookingResponse finish(long id) {
        BookingResponse result;
        try {
            result = bookings.confirm(id);
        } catch (RuntimeException failure) {
            log.error("event=booking_confirmation_commit_failed bookingId={}", id, failure);
            // Inspect the committed status under lock before deciding to release the slot.
            var cancellation = bookings.cancelPending(id, true);
            result = cancellation.booking();
            if (cancellation.changed()) {
                log.warn(
                        "event=booking_cancelled bookingId={} reason=confirmation_commit_failed",
                        id);
                recovery.compensate(id);
                throw new BookingOperationException(
                        503,
                        "BOOKING_CONFIRMATION_FAILED",
                        "Booking confirmation could not be saved",
                        id);
            }
        }
        if (result.status() != BookingStatus.CONFIRMED) {
            recovery.compensate(id);
            throw cancelled(id);
        }
        log.info("event=booking_confirmed bookingId={} roomId={}", id, result.roomId());
        return result;
    }

    private BookingOperationException fail(
            long id, boolean release, BookingOperationException failure) {
        var cancellation = bookings.cancelPending(id, release);
        if (cancellation.booking().compensationPending()) {
            recovery.compensate(id);
        }
        if (!cancellation.changed()) {
            return cancelled(id);
        }
        log.info("event=booking_cancelled bookingId={} reason={}", id, failure.code());
        return failure;
    }

    private void validateNew(CreateBookingRequest request) {
        if (request.autoSelect() == null) {
            throw new BookingInputException("autoSelect", "Room selection mode is required");
        }
        if (!request.autoSelect() && (request.roomId() == null || request.roomId() <= 0)) {
            throw new BookingInputException("roomId", "Manual booking requires a positive room id");
        }
        if (request.hotelId() != null && request.hotelId() <= 0) {
            throw new BookingInputException("hotelId", "Hotel id must be positive");
        }
        dates.requireBookable(request.startDate(), request.endDate());
    }

    private static BookingOperationException remoteError(HotelClientException failure, Long id) {
        if (failure.ambiguous() && failure.status() < 500) {
            return new BookingOperationException(
                    503, "HOTEL_INVALID_RESPONSE", "Hotel did not give a conclusive result", id);
        }
        return new BookingOperationException(
                failure.status(), failure.code(), failure.getMessage(), id);
    }

    private static BookingOperationException cancelled(long id) {
        return new BookingOperationException(
                409, "BOOKING_CANCELLED", "Booking was cancelled during confirmation", id);
    }

    private static BookingOperationException confirmError(HotelClientException failure, long id) {
        if (!failure.ambiguous() && (failure.status() == 401 || failure.status() == 403)) {
            return new BookingOperationException(
                    503, "HOTEL_AUTH_REJECTED", "Hotel rejected Booking service credentials", id);
        }
        return remoteError(failure, id);
    }

    public record Creation(BookingResponse booking, boolean created) {}
}
