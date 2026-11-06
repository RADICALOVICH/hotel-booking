package ru.hotelbooking.booking.booking.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import ru.hotelbooking.booking.booking.exception.BookingInputException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Component
class StayDates {
    private final Clock clock;

    StayDates(@Qualifier("jwtClock") Clock clock) {
        this.clock = clock.withZone(ZoneId.of("Europe/Moscow"));
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    void requireBookable(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new BookingInputException("startDate", "Check-in date is required");
        }
        if (endDate == null) {
            throw new BookingInputException("endDate", "Check-out date is required");
        }
        if (!endDate.isAfter(startDate)) {
            throw new BookingInputException("endDate", "Check-out must be after check-in");
        }
        LocalDate today = today();
        if (startDate.isBefore(today)) {
            throw new BookingInputException("startDate", "Check-in must not be in the past");
        }
        if (startDate.isAfter(today.plusDays(365))) {
            throw new BookingInputException("startDate", "Check-in must be within 365 days");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 30) {
            throw new BookingInputException("endDate", "A stay must not exceed 30 nights");
        }
    }
}
