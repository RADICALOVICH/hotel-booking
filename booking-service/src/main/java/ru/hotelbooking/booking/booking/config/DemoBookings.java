package ru.hotelbooking.booking.booking.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ru.hotelbooking.booking.user.entity.User;
import ru.hotelbooking.booking.user.entity.UserRole;
import ru.hotelbooking.booking.user.repository.UserRepository;
import ru.hotelbooking.booking.user.service.UserService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
@Profile("demo")
class DemoBookings implements SmartInitializingSingleton {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final UserRepository users;
    private final UserService accounts;
    private final Clock clock;
    private final String anchor;
    private final String password;

    DemoBookings(
            JdbcTemplate jdbc,
            TransactionTemplate transaction,
            UserRepository users,
            UserService accounts,
            @Qualifier("jwtClock") Clock clock,
            @Value("${DEMO_DATE:}") String anchor,
            @Value("${DEMO_USER_PASSWORD:}") String password) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.users = users;
        this.accounts = accounts;
        this.clock = clock.withZone(MOSCOW);
        this.anchor = anchor;
        this.password = password;
    }

    @Override
    public void afterSingletonsInstantiated() {
        transaction.executeWithoutResult(status -> seed());
    }

    private void seed() {
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select exists(select 1 from bookings where id = 910001)",
                        Boolean.class))) {
            return;
        }
        if (password.isBlank()) {
            throw new IllegalStateException("Set DEMO_USER_PASSWORD for the demo profile");
        }
        LocalDate day = anchor.isBlank() ? LocalDate.now(clock) : LocalDate.parse(anchor);
        User alice = demoUser("demo-alice");
        User bob = demoUser("demo-bob");
        // IDs, rooms and relative dates match the demo allocations in Hotel.
        booking(910001, alice, 910001, day.minusDays(12), day.minusDays(9), "CONFIRMED");
        booking(910002, bob, 910001, day.minusDays(6), day.minusDays(3), "CONFIRMED");
        booking(910003, bob, 910003, day.minusDays(9), day.minusDays(7), "CONFIRMED");
        booking(910004, alice, 910004, day.plusDays(2), day.plusDays(5), "CONFIRMED");
        booking(910005, alice, 910002, day.plusDays(4), day.plusDays(6), "CANCELLED");
    }

    private User demoUser(String username) {
        return users.findByUsername(username)
                .orElseGet(() -> accounts.createAccount(username, password, UserRole.USER));
    }

    private void booking(
            long id, User user, long roomId, LocalDate start, LocalDate end, String status) {
        OffsetDateTime created = start.minusDays(7).atStartOfDay(MOSCOW).toOffsetDateTime();
        jdbc.update(
                """
                insert into bookings (id, user_id, room_id, start_date, end_date, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                user.getId(),
                roomId,
                start,
                end,
                status,
                created,
                created);
    }
}
