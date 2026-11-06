package ru.hotelbooking.booking.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.hotelbooking.booking.booking.client.RecommendedRoom;
import ru.hotelbooking.booking.booking.dto.BookingResponse;
import ru.hotelbooking.booking.booking.dto.CreateBookingRequest;
import ru.hotelbooking.booking.booking.entity.BookingStatus;
import ru.hotelbooking.booking.booking.service.BookingRecovery;
import ru.hotelbooking.booking.booking.service.BookingSaga;
import ru.hotelbooking.booking.booking.service.BookingService;
import ru.hotelbooking.support.web.ErrorDTO;
import ru.hotelbooking.support.web.Pages;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class BookingController {
    private final BookingService bookings;
    private final BookingSaga saga;
    private final BookingRecovery recovery;

    public BookingController(BookingService bookings, BookingSaga saga, BookingRecovery recovery) {
        this.bookings = bookings;
        this.saga = saga;
        this.recovery = recovery;
    }

    @PostMapping("/booking")
    @Operation(summary = "Create a booking or replay an existing requestId")
    @ApiResponse(
            responseCode = "201",
            description = "New confirmed booking",
            useReturnTypeSchema = true,
            headers = @Header(name = "Location", description = "Path to read the booking"))
    @ApiResponse(responseCode = "200", description = "Existing booking", useReturnTypeSchema = true)
    @ApiResponse(
            responseCode = "202",
            description = "The original request is still pending",
            useReturnTypeSchema = true,
            headers = @Header(name = "Location", description = "Path to poll the booking"))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid booking details",
            content = @Content(schema = @Schema(implementation = ErrorDTO.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Room not found",
            content = @Content(schema = @Schema(implementation = ErrorDTO.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Room unavailable or requestId conflict",
            content = @Content(schema = @Schema(implementation = ErrorDTO.class)))
    @ApiResponse(
            responseCode = "503",
            description = "Hotel unavailable or circuit open",
            content = @Content(schema = @Schema(implementation = ErrorDTO.class)))
    @ApiResponse(
            responseCode = "504",
            description = "Hotel or booking deadline exceeded",
            content = @Content(schema = @Schema(implementation = ErrorDTO.class)))
    public ResponseEntity<BookingResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @RequestBody CreateBookingRequest request) {
        var result = saga.create(currentUserId(token), request, token.getTokenValue());
        BookingResponse booking = result.booking();
        HttpStatus status = HttpStatus.OK;
        if (result.created()) {
            status = HttpStatus.CREATED;
        } else if (booking.status() == BookingStatus.PENDING) {
            status = HttpStatus.ACCEPTED;
        }
        return ResponseEntity.status(status)
                .location(URI.create("/booking/" + booking.id()))
                .body(booking);
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingResponse>> history(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Pages.response(
                bookings.history(
                        currentUserId(token),
                        Pages.request(page, size, BookingService.HISTORY_ORDER)));
    }

    @GetMapping("/booking/{id}")
    public BookingResponse get(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return bookings.get(currentUserId(token), id);
    }

    @DeleteMapping("/booking/{id}")
    public BookingResponse cancel(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return saga.cancel(currentUserId(token), id);
    }

    @GetMapping("/rooms/recommend")
    public ResponseEntity<List<RecommendedRoom>> recommend(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(required = false) @Positive Long hotelId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pagination = Pages.request(page, size, Sort.unsorted());
        var rooms =
                saga.recommend(
                        startDate,
                        endDate,
                        hotelId,
                        pagination.getPageNumber(),
                        pagination.getPageSize(),
                        token.getTokenValue());
        return ResponseEntity.ok()
                .header("X-Total-Count", Long.toString(rooms.totalCount()))
                .body(rooms.rooms());
    }

    @PostMapping("/booking/{id}/compensation")
    @PreAuthorize("hasRole('ADMIN')")
    public BookingResponse restartCompensation(@PathVariable long id) {
        return recovery.restart(id);
    }

    private static long currentUserId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
