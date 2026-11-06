package ru.hotelbooking.booking.booking.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import ru.hotelbooking.booking.booking.exception.BookingInputException;
import ru.hotelbooking.support.dates.StayDateRules;

import java.time.Clock;
import java.time.LocalDate;

@Component
class StayDates {
    private final StayDateRules rules;

    StayDates(@Qualifier("jwtClock") Clock clock) {
        this.rules =
                new StayDateRules(
                        clock.withZone(StayDateRules.BUSINESS_ZONE), BookingInputException::new);
    }

    LocalDate today() {
        return rules.today();
    }

    void requireBookable(LocalDate startDate, LocalDate endDate) {
        rules.requireBookable(startDate, endDate);
    }
}
