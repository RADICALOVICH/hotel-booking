package ru.hotelbooking.hotel.availability.service;

import org.springframework.stereotype.Component;

import ru.hotelbooking.hotel.availability.exception.InvalidStayDatesException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Stay rules D06: [check-in, check-out), from today in Moscow, up to 30 nights, 365 days ahead. */
@Component
public class StayDates {
    public static final int MAX_NIGHTS = 30;
    public static final int MAX_DAYS_AHEAD = 365;

    private final Clock clock;

    public StayDates(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public void requireBookable(LocalDate startDate, LocalDate endDate) {
        requireOrdered(startDate, endDate);
        LocalDate today = today();
        if (startDate.isBefore(today)) {
            throw new InvalidStayDatesException("startDate", "Check-in must not be in the past");
        }
        if (startDate.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new InvalidStayDatesException(
                    "startDate", "Check-in must be within " + MAX_DAYS_AHEAD + " days");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_NIGHTS) {
            throw new InvalidStayDatesException(
                    "endDate", "A stay must not exceed " + MAX_NIGHTS + " nights");
        }
    }

    public void requireOrdered(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new InvalidStayDatesException("startDate", "Check-in date is required");
        }
        if (endDate == null) {
            throw new InvalidStayDatesException("endDate", "Check-out date is required");
        }
        if (!endDate.isAfter(startDate)) {
            throw new InvalidStayDatesException("endDate", "Check-out must be after check-in");
        }
    }
}
