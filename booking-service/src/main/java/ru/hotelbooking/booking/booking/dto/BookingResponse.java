package ru.hotelbooking.booking.booking.dto;

import ru.hotelbooking.booking.booking.entity.Booking;
import ru.hotelbooking.booking.booking.entity.BookingStatus;

import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        long id,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        Instant createdAt) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getRoomId(),
                booking.getStartDate(),
                booking.getEndDate(),
                booking.getStatus(),
                booking.getCreatedAt());
    }
}
