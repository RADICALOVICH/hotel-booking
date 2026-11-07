package ru.hotelbooking.booking.booking.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.hotelbooking.booking.booking.exception.BookingInputException;
import ru.hotelbooking.booking.booking.service.BookingService;
import ru.hotelbooking.support.dates.BookedStay;
import ru.hotelbooking.support.security.OccupancyJwt;

import java.time.LocalDate;
import java.util.List;

@RestController
@PreAuthorize("hasRole('OCCUPANCY')")
public class BookingOccupancyController {
    private final BookingService bookings;

    public BookingOccupancyController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping(OccupancyJwt.PATH)
    public List<BookedStay> occupied(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (!endDate.isAfter(startDate)) {
            throw new BookingInputException("endDate", "End must follow start");
        }
        return bookings.occupied(startDate, endDate);
    }
}
