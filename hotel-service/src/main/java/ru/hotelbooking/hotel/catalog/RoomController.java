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
    public List<RoomResponse> list() {
        return rooms.list();
    }
}
