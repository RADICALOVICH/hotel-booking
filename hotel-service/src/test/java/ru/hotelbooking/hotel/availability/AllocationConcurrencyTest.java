package ru.hotelbooking.hotel.availability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import ru.hotelbooking.hotel.availability.dto.AllocationRequest;
import ru.hotelbooking.hotel.availability.exception.AllocationConflictException;
import ru.hotelbooking.hotel.availability.service.AvailabilityService;
import ru.hotelbooking.hotel.catalog.dto.PatchRoomRequest;
import ru.hotelbooking.hotel.catalog.exception.RoomInUseException;
import ru.hotelbooking.hotel.catalog.service.RoomService;
import ru.hotelbooking.hotel.support.CatalogFixture;
import ru.hotelbooking.hotel.support.Concurrently;
import ru.hotelbooking.hotel.support.Concurrently.Outcome;
import ru.hotelbooking.hotel.support.HotelTestConfig;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Real threads and real H2 transactions; nothing here is mocked. */
@SpringBootTest
@Import({HotelTestConfig.class, CatalogFixture.class})
@ActiveProfiles("test")
class AllocationConcurrencyTest {
    private static final int THREADS = 8;
    private static final LocalDate START = HotelTestConfig.TODAY.plusDays(30);

    private final AvailabilityService availability;
    private final RoomService rooms;
    private final CatalogFixture catalog;
    private final JdbcTemplate jdbc;

    @Autowired
    AllocationConcurrencyTest(
            AvailabilityService availability,
            RoomService rooms,
            CatalogFixture catalog,
            JdbcTemplate jdbc) {
        this.availability = availability;
        this.rooms = rooms;
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    @RepeatedTest(5)
    void overlappingAttemptsFromManyThreadsLeaveOneHolder() throws Exception {
        long roomId = catalog.room(catalog.hotel(), "301");
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            AllocationRequest attempt = attempt(START.plusDays(i % 2), START.plusDays(3));
            tasks.add(() -> availability.confirm(roomId, attempt).state().name());
        }

        List<Outcome> outcomes = Concurrently.run(tasks);

        assertThat(outcomes).extracting(Outcome::failure).containsOnlyNulls();
        assertThat(outcomes).filteredOn(o -> o.value().equals("ACTIVE")).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.value().equals("REJECTED")).hasSize(THREADS - 1);
        assertThat(activeCount(roomId)).isEqualTo(1);
        assertThat(catalog.reload(roomId).getTimesBooked()).isEqualTo(1);
    }

    @Test
    void duplicateDeliveriesOfOneAttemptCreateOneAllocation() throws Exception {
        long roomId = catalog.room(catalog.hotel(), "302");
        AllocationRequest attempt = attempt(START, START.plusDays(2));
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> availability.confirm(roomId, attempt).state().name());
        }

        List<Outcome> outcomes = Concurrently.run(tasks);

        assertThat(outcomes).extracting(Outcome::value).containsOnly("ACTIVE");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from room_allocations where allocation_id = ?",
                                Integer.class,
                                attempt.allocationId()))
                .isEqualTo(1);
        assertThat(catalog.reload(roomId).getTimesBooked()).isEqualTo(1);
    }

    @RepeatedTest(10)
    void confirmRacingReleaseNeverLeavesTheDatesHeld() throws Exception {
        long roomId = catalog.room(catalog.hotel(), "303");
        AllocationRequest attempt = attempt(START, START.plusDays(2));

        List<Outcome> outcomes =
                Concurrently.run(
                        List.of(
                                () -> confirmOutcome(roomId, attempt),
                                () -> availability.release(roomId, attempt).state().name()));

        assertThat(outcomes.get(1).value()).isEqualTo("RELEASED");
        assertThat(outcomes.get(0).value()).isIn("ACTIVE", "RELEASED");
        assertThat(activeCount(roomId)).isZero();
        assertThat(catalog.reload(roomId).getTimesBooked()).isZero();
    }

    @RepeatedTest(10)
    void disablingRacingConfirmHasExactlyOneWinner() throws Exception {
        long roomId = catalog.room(catalog.hotel(), "304");
        AllocationRequest attempt = attempt(START, START.plusDays(2));

        List<Outcome> outcomes =
                Concurrently.run(
                        List.of(
                                () -> confirmOutcome(roomId, attempt),
                                () -> disableOutcome(roomId)));

        boolean held = outcomes.get(0).value().equals("ACTIVE");
        boolean disabled = outcomes.get(1).value().equals("DISABLED");
        assertThat(held ^ disabled).as("exactly one operation wins: %s", outcomes).isTrue();
        assertThat(catalog.reload(roomId).isAvailable()).isEqualTo(held);
        assertThat(activeCount(roomId)).isEqualTo(held ? 1 : 0);
    }

    private String confirmOutcome(long roomId, AllocationRequest attempt) {
        try {
            return availability.confirm(roomId, attempt).state().name();
        } catch (AllocationConflictException conflict) {
            return conflict.getCode();
        }
    }

    private String disableOutcome(long roomId) {
        try {
            rooms.update(roomId, new PatchRoomRequest(null, false));
            return "DISABLED";
        } catch (RoomInUseException conflict) {
            return conflict.getCode();
        }
    }

    private int activeCount(long roomId) {
        return jdbc.queryForObject(
                "select count(*) from room_allocations where room_id = ? and state = 'ACTIVE'",
                Integer.class,
                roomId);
    }

    private static AllocationRequest attempt(LocalDate start, LocalDate end) {
        return CatalogFixture.attempt(CatalogFixture.newAllocationId(), start, end);
    }
}
