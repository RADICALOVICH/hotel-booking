package ru.hotelbooking.hotel.support;

import ru.hotelbooking.hotel.availability.client.BookingOccupancy;
import ru.hotelbooking.hotel.availability.exception.BookingOccupancyUnavailableException;
import ru.hotelbooking.support.dates.BookedStay;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class StubBookingOccupancy implements BookingOccupancy {
    private final List<BookedStay> confirmed = new CopyOnWriteArrayList<>();
    private volatile boolean unavailable;

    public BookedStay add(long room, LocalDate start, LocalDate end) {
        var stay = new BookedStay(room, start, end);
        confirmed.add(stay);
        return stay;
    }

    public void remove(BookedStay stay) {
        confirmed.remove(stay);
    }

    public void unavailable(boolean value) {
        unavailable = value;
    }

    @Override
    public List<BookedStay> find(LocalDate startDate, LocalDate endDate) {
        if (unavailable) {
            throw new BookingOccupancyUnavailableException(
                    new IllegalStateException("Test outage"));
        }
        return confirmed.stream()
                .filter(
                        stay ->
                                stay.startDate().isBefore(endDate)
                                        && stay.endDate().isAfter(startDate))
                .toList();
    }
}
