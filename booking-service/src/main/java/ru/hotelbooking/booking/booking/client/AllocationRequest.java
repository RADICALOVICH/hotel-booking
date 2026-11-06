package ru.hotelbooking.booking.booking.client;

import java.time.LocalDate;

public record AllocationRequest(
        long bookingId, String allocationId, LocalDate startDate, LocalDate endDate) {}
