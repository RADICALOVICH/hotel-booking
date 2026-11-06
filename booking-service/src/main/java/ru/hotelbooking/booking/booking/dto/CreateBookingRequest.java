package ru.hotelbooking.booking.booking.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBookingRequest(
        UUID requestId,
        Boolean autoSelect,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        Long hotelId) {
    public CreateBookingRequest {
        if (Boolean.TRUE.equals(autoSelect)) {
            roomId = null;
        }
    }
}
