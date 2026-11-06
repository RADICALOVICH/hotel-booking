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
import ru.hotelbooking.support.demo.DemoStays;
import ru.hotelbooking.support.demo.DemoStays.Stay;

import java.time.Clock;
import java.util.HashMap;

@Component
@Profile("demo")
class DemoBookings implements SmartInitializingSingleton {
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
        this.clock = clock;
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
        var guests = new HashMap<String, User>();
        for (Stay stay : DemoStays.at(anchor, clock)) {
            booking(stay, guests.computeIfAbsent(stay.username(), this::demoUser));
        }
    }

    private User demoUser(String username) {
        return users.findByUsername(username)
                .orElseGet(() -> accounts.createAccount(username, password, UserRole.USER));
    }

    private void booking(Stay stay, User user) {
        jdbc.update(
                """
                insert into bookings (id, user_id, room_id, start_date, end_date, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                stay.bookingId(),
                user.getId(),
                stay.roomId(),
                stay.startDate(),
                stay.endDate(),
                stay.cancelled() ? "CANCELLED" : "CONFIRMED",
                stay.createdAt(),
                stay.createdAt());
    }
}
