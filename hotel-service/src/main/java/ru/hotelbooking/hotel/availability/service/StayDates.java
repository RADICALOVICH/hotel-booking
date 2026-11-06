package ru.hotelbooking.hotel.availability.service;

import org.springframework.stereotype.Component;

import ru.hotelbooking.hotel.availability.exception.InvalidStayDatesException;
import ru.hotelbooking.support.dates.StayDateRules;

import java.time.Clock;
import java.time.LocalDate;

/** Stay rules D06: [check-in, check-out), from today in Moscow, up to 30 nights, 365 days ahead. */
@Component
public class StayDates {
    private final StayDateRules rules;

    public StayDates(Clock clock) {
        this.rules = new StayDateRules(clock, InvalidStayDatesException::new);
    }

    public LocalDate today() {
        return rules.today();
    }

    public void requireBookable(LocalDate startDate, LocalDate endDate) {
        rules.requireBookable(startDate, endDate);
    }

    public void requireOrdered(LocalDate startDate, LocalDate endDate) {
        rules.requireOrdered(startDate, endDate);
    }
}
