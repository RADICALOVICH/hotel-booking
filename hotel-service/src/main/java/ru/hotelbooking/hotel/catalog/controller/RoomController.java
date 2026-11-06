package ru.hotelbooking.hotel.catalog.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.hotelbooking.hotel.catalog.dto.CreateRoomRequest;
import ru.hotelbooking.hotel.catalog.dto.PatchRoomRequest;
import ru.hotelbooking.hotel.catalog.dto.RoomResponse;
import ru.hotelbooking.hotel.catalog.service.RoomService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        RoomResponse room = rooms.create(request);
        return ResponseEntity.created(URI.create("/api/rooms/" + room.id())).body(room);
    }

    @GetMapping("/{id}")
    public RoomResponse get(@PathVariable long id) {
        return rooms.get(id);
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<RoomResponse>> list(
            @RequestParam(required = false) @Positive Long hotelId,
            @RequestParam(required = false) @Pattern(regexp = "true|false") String available,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Boolean availability = available == null ? null : Boolean.valueOf(available);
        return CatalogPages.response(
                rooms.list(hotelId, availability, CatalogPages.byId(page, size)));
    }

    @PatchMapping("/{id}")
    public RoomResponse update(
            @PathVariable long id, @Valid @RequestBody PatchRoomRequest request) {
        return rooms.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        rooms.delete(id);
        return ResponseEntity.noContent().build();
    }
}
