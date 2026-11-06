package ru.hotelbooking.booking.booking.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.hotelbooking.booking.booking.dto.BookingResponse;
import ru.hotelbooking.booking.booking.service.BookingService;
import ru.hotelbooking.booking.common.web.Pages;

import java.util.List;

@RestController
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingResponse>> history(
            @AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Pages.response(
                bookings.history(
                        currentUserId(token),
                        Pages.request(page, size, BookingService.HISTORY_ORDER)));
    }

    @GetMapping("/booking/{id}")
    public BookingResponse get(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return bookings.get(currentUserId(token), id);
    }

    private static long currentUserId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
