package ru.hotelbooking.support.dates;

import java.time.LocalDate;

/** Anonymous occupancy read from Booking's confirmed bookings, never from Hotel allocations. */
public record BookedStay(long roomId, LocalDate startDate, LocalDate endDate) {
    public BookedStay {
        if (roomId <= 0 || startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("Invalid booked stay");
        }
    }
}
