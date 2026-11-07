package ru.hotelbooking.booking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import ru.hotelbooking.booking.booking.client.AllocationRequest;
import ru.hotelbooking.booking.booking.client.HotelClient;
import ru.hotelbooking.booking.booking.client.RecommendedRoom;
import ru.hotelbooking.booking.booking.client.RecommendedRooms;
import ru.hotelbooking.booking.booking.dto.CreateBookingRequest;
import ru.hotelbooking.booking.booking.exception.BookingOperationException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.stream.LongStream;

class RoomCandidatesTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Test
    void visitsLaterPagesWithoutRepeatingRejectedRooms() {
        List<RecommendedRoom> rooms = rooms(25);
        var hotel = new Recommendations(page -> page(rooms, page));
        var candidates = candidates(hotel, new AtomicLong());
        var visited = new ArrayList<Long>();

        for (var next = candidates.next(); next.isPresent(); next = candidates.next()) {
            visited.add(next.getAsLong());
        }

        assertThat(visited)
                .containsExactlyElementsOf(LongStream.rangeClosed(1, 25).boxed().toList());
        assertThat(hotel.pages).contains(2);
    }

    @Test
    void refreshesTheFirstPageAfterOccupiedRoomsDisappear() {
        var rooms = new ArrayList<>(rooms(30));
        var hotel = new Recommendations(page -> page(rooms, page));
        var candidates = candidates(hotel, new AtomicLong());

        for (long id = 1; id <= 10; id++) {
            assertThat(candidates.next().orElseThrow()).isEqualTo(id);
            rooms.removeFirst();
        }

        assertThat(candidates.next().orElseThrow()).isEqualTo(11);
        assertThat(hotel.pages).containsExactly(0, 0);
    }

    @Test
    void stopsAtTheDeadlineBeforeAnotherCandidateCanBeConfirmed() {
        var hotel = new Recommendations(page -> page(rooms(30), page));
        var elapsed = new AtomicLong();
        var candidates = candidates(hotel, elapsed);
        assertThat(candidates.next().orElseThrow()).isEqualTo(1);

        elapsed.set(TIMEOUT.toNanos());

        assertTimeout(candidates);
        assertThat(hotel.pages).containsExactly(0);
    }

    @Test
    void rejectsRecommendationsThatArriveAfterTheDeadline() {
        var elapsed = new AtomicLong();
        var hotel =
                new Recommendations(
                        page -> {
                            elapsed.set(TIMEOUT.toNanos());
                            return page(rooms(1), page);
                        });

        assertTimeout(candidates(hotel, elapsed));
    }

    private static void assertTimeout(RoomCandidates candidates) {
        assertThatThrownBy(candidates::next)
                .isInstanceOfSatisfying(
                        BookingOperationException.class,
                        failure -> {
                            assertThat(failure.status()).isEqualTo(504);
                            assertThat(failure.code()).isEqualTo("ROOM_SELECTION_TIMEOUT");
                            assertThat(failure.bookingId()).isEqualTo(42);
                        });
    }

    private static RoomCandidates candidates(HotelClient hotel, AtomicLong elapsed) {
        var request =
                new CreateBookingRequest(
                        UUID.randomUUID(),
                        true,
                        null,
                        LocalDate.of(2026, 12, 10),
                        LocalDate.of(2026, 12, 12),
                        8L);
        return new RoomCandidates(42, request, "user-token", hotel, TIMEOUT, elapsed::get);
    }

    private static List<RecommendedRoom> rooms(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(id -> new RecommendedRoom(id, 8, Long.toString(id), true, 0))
                .toList();
    }

    private static RecommendedRooms page(List<RecommendedRoom> rooms, int page) {
        int start = Math.min(page * 10, rooms.size());
        return new RecommendedRooms(
                List.copyOf(rooms.subList(start, Math.min(start + 10, rooms.size()))),
                rooms.size());
    }

    private static class Recommendations implements HotelClient {
        private final IntFunction<RecommendedRooms> response;
        private final List<Integer> pages = new ArrayList<>();

        Recommendations(IntFunction<RecommendedRooms> response) {
            this.response = response;
        }

        @Override
        public RecommendedRooms recommend(
                LocalDate startDate,
                LocalDate endDate,
                Long hotelId,
                int page,
                int size,
                String userToken) {
            pages.add(page);
            return response.apply(page);
        }

        @Override
        public void confirm(long roomId, AllocationRequest request) {
            throw new AssertionError("Candidate discovery must not allocate rooms");
        }

        @Override
        public void release(long roomId, AllocationRequest request) {
            throw new AssertionError("Candidate discovery must not release rooms");
        }

        @Override
        public void releaseOnce(long roomId, AllocationRequest request) {
            throw new AssertionError("Candidate discovery must not release rooms");
        }
    }
}
