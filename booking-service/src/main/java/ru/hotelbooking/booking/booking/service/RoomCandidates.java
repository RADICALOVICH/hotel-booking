package ru.hotelbooking.booking.booking.service;

import ru.hotelbooking.booking.booking.client.HotelClient;
import ru.hotelbooking.booking.booking.dto.CreateBookingRequest;
import ru.hotelbooking.booking.booking.exception.BookingOperationException;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.LongSupplier;

/** Per-booking traversal of a changing recommendation list, bounded by a monotonic deadline. */
final class RoomCandidates {
    private static final int PAGE_SIZE = 10;

    private final long bookingId;
    private final CreateBookingRequest request;
    private final String userToken;
    private final HotelClient hotel;
    private final LongSupplier nanoTime;
    private final long startedAt;
    private final long timeoutNanos;
    private final Set<Long> tried = new HashSet<>();
    private Iterator<Long> batch = Collections.emptyIterator();

    RoomCandidates(
            long bookingId,
            CreateBookingRequest request,
            String userToken,
            HotelClient hotel,
            Duration timeout,
            LongSupplier nanoTime) {
        this.bookingId = bookingId;
        this.request = request;
        this.userToken = userToken;
        this.hotel = hotel;
        this.nanoTime = nanoTime;
        this.startedAt = nanoTime.getAsLong();
        this.timeoutNanos = timeout.toNanos();
    }

    OptionalLong next() {
        requireTime();
        if (!request.autoSelect()) {
            return tried.add(request.roomId())
                    ? OptionalLong.of(request.roomId())
                    : OptionalLong.empty();
        }
        if (!batch.hasNext()) {
            batch = loadBatch().iterator();
        }
        if (!batch.hasNext()) {
            return OptionalLong.empty();
        }
        long roomId = batch.next();
        tried.add(roomId);
        return OptionalLong.of(roomId);
    }

    private List<Long> loadBatch() {
        // Occupied rooms disappear from the result, shifting the following pages to the left.
        // Restart at page zero after each batch; skip IDs already tried by this booking.
        for (int page = 0; ; page++) {
            requireTime();
            var response =
                    hotel.recommend(
                            request.startDate(),
                            request.endDate(),
                            request.hotelId(),
                            page,
                            PAGE_SIZE,
                            userToken);
            requireTime();
            List<Long> candidates =
                    response.rooms().stream()
                            .map(room -> room.id())
                            .filter(id -> !tried.contains(id))
                            .distinct()
                            .toList();
            if (!candidates.isEmpty()) {
                return candidates;
            }
            long consumed = (long) page * PAGE_SIZE + response.rooms().size();
            if (response.rooms().isEmpty() || consumed >= response.totalCount()) {
                return List.of();
            }
        }
    }

    private void requireTime() {
        if (nanoTime.getAsLong() - startedAt >= timeoutNanos) {
            throw new BookingOperationException(
                    504,
                    "ROOM_SELECTION_TIMEOUT",
                    "Room selection exceeded its time limit; retry with a new requestId",
                    bookingId);
        }
    }
}
