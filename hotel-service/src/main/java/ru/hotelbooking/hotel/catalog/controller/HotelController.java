package ru.hotelbooking.hotel.catalog.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.hotelbooking.hotel.catalog.dto.CreateHotelRequest;
import ru.hotelbooking.hotel.catalog.dto.HotelResponse;
import ru.hotelbooking.hotel.catalog.dto.PatchHotelRequest;
import ru.hotelbooking.hotel.catalog.service.HotelService;
import ru.hotelbooking.hotel.common.web.Pages;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {
    private static final String NON_BLANK = "(?s).*\\P{javaWhitespace}.*";

    private final HotelService hotels;

    public HotelController(HotelService hotels) {
        this.hotels = hotels;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ApiResponse(responseCode = "201", description = "Hotel created", useReturnTypeSchema = true)
    public ResponseEntity<HotelResponse> create(@Valid @RequestBody CreateHotelRequest request) {
        HotelResponse hotel = hotels.create(request);
        return ResponseEntity.created(URI.create("/api/hotels/" + hotel.id())).body(hotel);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<List<HotelResponse>> list(
            @RequestParam(required = false) @Pattern(regexp = NON_BLANK) @Size(max = 255)
                    String name,
            @RequestParam(required = false) @Pattern(regexp = NON_BLANK) @Size(max = 255)
                    String address,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Pages.response(hotels.list(name, address, Pages.request(page, size, Sort.by("id"))));
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/{id}")
    public HotelResponse get(@PathVariable long id) {
        return hotels.get(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public HotelResponse update(
            @PathVariable long id, @Valid @RequestBody PatchHotelRequest request) {
        return hotels.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "Hotel removed")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        hotels.delete(id);
        return ResponseEntity.noContent().build();
    }
}
