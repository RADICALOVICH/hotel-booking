package ru.hotelbooking.hotel.catalog;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {
    private final HotelService hotels;

    public HotelController(HotelService hotels) {
        this.hotels = hotels;
    }

    @PostMapping
    public ResponseEntity<HotelResponse> create(@Valid @RequestBody CreateHotelRequest request) {
        HotelResponse hotel = hotels.create(request);
        return ResponseEntity.created(URI.create("/api/hotels/" + hotel.id())).body(hotel);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HotelResponse> find(@PathVariable long id) {
        return ResponseEntity.of(hotels.find(id));
    }
}
