package ru.hotelbooking.hotel.availability.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.hotelbooking.hotel.availability.dto.RoomStatisticsResponse;
import ru.hotelbooking.hotel.availability.service.RoomSearchService;
import ru.hotelbooking.hotel.catalog.dto.RoomResponse;
import ru.hotelbooking.hotel.common.web.Pages;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomSearchController {
    private final RoomSearchService search;

    public RoomSearchController(RoomSearchService search) {
        this.search = search;
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<List<RoomResponse>> free(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(required = false) @Positive Long hotelId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return find(hotelId, startDate, endDate, page, size, RoomSearchService.BY_ID);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/recommend")
    public ResponseEntity<List<RoomResponse>> recommend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(required = false) @Positive Long hotelId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return find(hotelId, startDate, endDate, page, size, RoomSearchService.RECOMMENDED);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/statistics")
    public ResponseEntity<List<RoomStatisticsResponse>> statistics(
            @RequestParam(required = false) @Positive Long hotelId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Pages.response(
                search.statistics(hotelId, Pages.request(page, size, RoomSearchService.BY_ID)));
    }

    private ResponseEntity<List<RoomResponse>> find(
            Long hotelId, LocalDate startDate, LocalDate endDate, int page, int size, Sort order) {
        return Pages.response(
                search.free(hotelId, startDate, endDate, Pages.request(page, size, order)));
    }
}
