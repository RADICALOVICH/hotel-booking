package ru.hotelbooking.hotel.availability.client;

import ru.hotelbooking.support.dates.BookedStay;

import java.time.LocalDate;
import java.util.List;

/** Authoritative confirmed occupancy from Booking's BOOKINGS table. */
public interface BookingOccupancy {
    List<BookedStay> find(LocalDate startDate, LocalDate endDate);
}
