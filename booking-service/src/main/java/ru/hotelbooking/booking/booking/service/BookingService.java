package ru.hotelbooking.booking.booking.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hotelbooking.booking.booking.dto.BookingResponse;
import ru.hotelbooking.booking.booking.exception.BookingNotFoundException;
import ru.hotelbooking.booking.booking.repository.BookingRepository;
import ru.hotelbooking.support.dates.BookedStay;

import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {
    public static final Sort HISTORY_ORDER =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final BookingRepository bookings;

    BookingService(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public List<BookedStay> occupied(LocalDate startDate, LocalDate endDate) {
        return bookings.findConfirmedStays(startDate, endDate);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> history(long userId, Pageable pageable) {
        return bookings.findByUserId(userId, pageable).map(BookingResponse::from);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(long userId, long bookingId) {
        return bookings.findByIdAndUserId(bookingId, userId)
                .map(BookingResponse::from)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }
}
