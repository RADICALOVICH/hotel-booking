package ru.hotelbooking.support.demo;

import static ru.hotelbooking.support.dates.StayDateRules.BUSINESS_ZONE;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class DemoStays {
    private static final String ALICE = "demo-alice";
    private static final String BOB = "demo-bob";

    private DemoStays() {}

    public static List<Stay> at(String anchor, Clock clock) {
        LocalDate day =
                anchor.isBlank()
                        ? LocalDate.now(clock.withZone(BUSINESS_ZONE))
                        : LocalDate.parse(anchor);
        return List.of(
                stay(day, 910001, ALICE, 910001, -12, -9, false),
                stay(day, 910002, BOB, 910001, -6, -3, false),
                stay(day, 910003, BOB, 910003, -9, -7, false),
                stay(day, 910004, ALICE, 910004, 2, 5, false),
                stay(day, 910005, ALICE, 910002, 4, 6, true));
    }

    private static Stay stay(
            LocalDate day,
            long bookingId,
            String username,
            long roomId,
            int startOffset,
            int endOffset,
            boolean cancelled) {
        LocalDate startDate = day.plusDays(startOffset);
        OffsetDateTime createdAt =
                startDate.minusDays(7).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        return new Stay(
                bookingId,
                username,
                roomId,
                startDate,
                day.plusDays(endOffset),
                createdAt,
                cancelled);
    }

    public record Stay(
            long bookingId,
            String username,
            long roomId,
            LocalDate startDate,
            LocalDate endDate,
            OffsetDateTime createdAt,
            boolean cancelled) {}
}
