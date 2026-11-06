package ru.hotelbooking.support.dates;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.function.BiFunction;

public final class StayDateRules {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Moscow");

    private static final int MAX_NIGHTS = 30;
    private static final int MAX_DAYS_AHEAD = 365;

    private final Clock clock;
    private final BiFunction<String, String, ? extends RuntimeException> invalid;

    public StayDateRules(
            Clock clock, BiFunction<String, String, ? extends RuntimeException> invalid) {
        this.clock = clock;
        this.invalid = invalid;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public void requireBookable(LocalDate startDate, LocalDate endDate) {
        requireOrdered(startDate, endDate);
        LocalDate today = today();
        if (startDate.isBefore(today)) {
            throw invalid.apply("startDate", "Check-in must not be in the past");
        }
        if (startDate.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw invalid.apply("startDate", "Check-in must be within " + MAX_DAYS_AHEAD + " days");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_NIGHTS) {
            throw invalid.apply("endDate", "A stay must not exceed " + MAX_NIGHTS + " nights");
        }
    }

    public void requireOrdered(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw invalid.apply("startDate", "Check-in date is required");
        }
        if (endDate == null) {
            throw invalid.apply("endDate", "Check-out date is required");
        }
        if (!endDate.isAfter(startDate)) {
            throw invalid.apply("endDate", "Check-out must be after check-in");
        }
    }
}
