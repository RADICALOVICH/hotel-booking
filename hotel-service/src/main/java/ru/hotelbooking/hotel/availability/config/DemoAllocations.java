package ru.hotelbooking.hotel.availability.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ru.hotelbooking.support.allocation.AllocationIds;
import ru.hotelbooking.support.demo.DemoStays;
import ru.hotelbooking.support.demo.DemoStays.Stay;

import java.time.Clock;

@Component
@Profile("demo")
class DemoAllocations implements SmartInitializingSingleton {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String anchor;

    DemoAllocations(
            JdbcTemplate jdbc,
            TransactionTemplate transaction,
            Clock clock,
            @Value("${DEMO_DATE:}") String anchor) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.clock = clock;
        this.anchor = anchor;
    }

    @Override
    public void afterSingletonsInstantiated() {
        transaction.executeWithoutResult(status -> seed());
    }

    private void seed() {
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select exists(select 1 from hotels where id = 910001)", Boolean.class))) {
            return;
        }
        var stays = DemoStays.at(anchor, clock);
        jdbc.update(
                "insert into hotels (id, name, address) values (?, ?, ?)",
                910001,
                "Маяк",
                "Морская улица, 7");
        jdbc.update(
                "insert into hotels (id, name, address) values (?, ?, ?)",
                910002,
                "Парк",
                "Парковая улица, 12");
        room(910001, 910001, "101", true);
        room(910002, 910001, "102", true);
        room(910003, 910001, "103", false);
        room(910004, 910002, "201", true);
        room(910005, 910002, "202", true);
        // Historical stays predate normal confirm validation.
        stays.forEach(this::allocation);
    }

    private void room(long id, long hotelId, String number, boolean available) {
        jdbc.update(
                """
                insert into rooms (id, hotel_id, number, available, times_booked) values (?, ?, ?, ?, 0)
                """,
                id,
                hotelId,
                number,
                available);
    }

    private void allocation(Stay stay) {
        boolean active = !stay.cancelled();
        jdbc.update(
                """
                insert into room_allocations (allocation_id, booking_id, room_id, start_date, end_date,
                    state, created_at, released_at) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                AllocationIds.forBookingRoom(stay.bookingId(), stay.roomId()),
                stay.bookingId(),
                stay.roomId(),
                stay.startDate(),
                stay.endDate(),
                active ? "ACTIVE" : "RELEASED",
                stay.createdAt(),
                active ? null : stay.createdAt().plusDays(1));
        if (active) {
            jdbc.update(
                    "update rooms set times_booked = times_booked + 1 where id = ?", stay.roomId());
        }
    }
}
