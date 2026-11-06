package ru.hotelbooking.hotel.availability.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.hotelbooking.hotel.availability.dto.HotelOccupancyResponse;
import ru.hotelbooking.hotel.availability.dto.RoomOccupancyResponse;
import ru.hotelbooking.hotel.availability.dto.RoomStatisticsResponse;
import ru.hotelbooking.hotel.availability.service.OccupancyService;
import ru.hotelbooking.hotel.availability.service.RoomSearchService;
import ru.hotelbooking.support.web.Pages;

import java.time.LocalDate;
import java.util.List;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Occupancy")
@SecurityRequirement(name = "bearerAuth")
public class OccupancyController {
    private final RoomSearchService search;
    private final OccupancyService occupancy;

    public OccupancyController(RoomSearchService search, OccupancyService occupancy) {
        this.search = search;
        this.occupancy = occupancy;
    }

    @GetMapping("/api/rooms/statistics")
    @Operation(
            summary = "Report occupancy by room",
            description =
                    """
                    ADMIN only. Omit both dates for the current counters. Supply both dates for a
                    report over [startDate, endDate). The available and sort filters apply to the report.
                    Allowed sort fields are id, timesBooked, allocationCount, occupiedRoomNights and
                    occupancyPercent. Append ,desc for descending order.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Room snapshot or occupancy report",
            headers = @Header(name = "X-Total-Count", description = "Total matching rooms"),
            content =
                    @Content(
                            array =
                                    @ArraySchema(
                                            schema =
                                                    @Schema(
                                                            oneOf = {
                                                                RoomStatisticsResponse.class,
                                                                RoomOccupancyResponse.class
                                                            })),
                            examples =
                                    @ExampleObject(
                                            name = "Room report",
                                            value =
                                                    """
                            [{"roomId":1,"hotelId":1,"number":"101","available":true,
                              "timesBooked":3,"allocationCount":1,"occupiedRoomNights":2,
                              "occupiedAvailableRoomNights":2,"capacityRoomNights":5,"occupancyPercent":40.00}]
                            """)))
    public ResponseEntity<? extends List<?>> rooms(
            @Parameter(example = "2026-11-01")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @Parameter(example = "2026-11-06")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(required = false) @Positive Long hotelId,
            @RequestParam(required = false) @Pattern(regexp = "true|false") String available,
            @Parameter(example = "occupiedRoomNights,desc") @RequestParam(defaultValue = "id")
                    String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (startDate == null && endDate == null) {
            return Pages.response(
                    search.statistics(hotelId, Pages.request(page, size, RoomSearchService.BY_ID)));
        }
        return Pages.response(
                occupancy.rooms(
                        startDate,
                        endDate,
                        hotelId,
                        availability(available),
                        sort,
                        Pages.request(page, size, Sort.unsorted())));
    }

    @GetMapping("/api/hotels/statistics")
    @Operation(
            summary = "Report occupancy by hotel",
            description =
                    """
                    ADMIN only. Report over [startDate, endDate). Occupied nights include every
                    matching room. The occupancy percentage uses the occupied nights and capacity
                    of currently available rooms, and is null when capacity is zero. The spread is
                    the difference between the largest and smallest timesBooked among those rooms.
                    Allowed sort fields are id, timesBookedSpread, allocationCount, occupiedRoomNights
                    and occupancyPercent. Append ,desc for descending order.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Hotel occupancy report",
            headers = @Header(name = "X-Total-Count", description = "Total matching hotels"),
            content =
                    @Content(
                            array =
                                    @ArraySchema(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    HotelOccupancyResponse.class)),
                            examples =
                                    @ExampleObject(
                                            name = "Hotel report",
                                            value =
                                                    """
                            [{"hotelId":1,"hotelName":"Hotel","roomCount":3,"availableRoomCount":2,
                              "allocationCount":2,"occupiedRoomNights":5,"occupiedAvailableRoomNights":2,
                              "capacityRoomNights":10,"occupancyPercent":20.00,"timesBookedSpread":2}]
                            """)))
    public ResponseEntity<List<HotelOccupancyResponse>> hotels(
            @Parameter(required = true, example = "2026-11-01")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @Parameter(required = true, example = "2026-11-06")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(required = false) @Positive Long hotelId,
            @RequestParam(required = false) @Pattern(regexp = "true|false") String available,
            @Parameter(example = "occupancyPercent,desc") @RequestParam(defaultValue = "id")
                    String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Pages.response(
                occupancy.hotels(
                        startDate,
                        endDate,
                        hotelId,
                        availability(available),
                        sort,
                        Pages.request(page, size, Sort.unsorted())));
    }

    private static Boolean availability(String available) {
        return available == null ? null : Boolean.valueOf(available);
    }
}
