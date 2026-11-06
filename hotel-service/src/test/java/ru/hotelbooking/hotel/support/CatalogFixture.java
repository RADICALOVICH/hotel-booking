package ru.hotelbooking.hotel.support;

import org.springframework.boot.test.context.TestComponent;

import ru.hotelbooking.hotel.availability.dto.AllocationRequest;
import ru.hotelbooking.hotel.catalog.entity.Hotel;
import ru.hotelbooking.hotel.catalog.entity.Room;
import ru.hotelbooking.hotel.catalog.repository.HotelRepository;
import ru.hotelbooking.hotel.catalog.repository.RoomRepository;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/** Stores hotels and rooms directly, so calendar tests start from a known catalog. */
@TestComponent
public class CatalogFixture {
    private static final AtomicLong SEQUENCE = new AtomicLong(1_000);

    private final HotelRepository hotels;
    private final RoomRepository rooms;

    CatalogFixture(HotelRepository hotels, RoomRepository rooms) {
        this.hotels = hotels;
        this.rooms = rooms;
    }

    public long hotel() {
        return hotels.save(new Hotel("Fixture " + SEQUENCE.incrementAndGet(), "Address")).getId();
    }

    public long room(long hotelId, String number) {
        Hotel hotel = hotels.findById(hotelId).orElseThrow();
        return rooms.save(new Room(hotel, number)).getId();
    }

    public Room reload(long roomId) {
        return rooms.findById(roomId).orElseThrow();
    }

    public static AllocationRequest attempt(String allocationId, LocalDate start, LocalDate end) {
        return new AllocationRequest(bookingIdOf(allocationId), allocationId, start, end);
    }

    public static String newAllocationId() {
        return "test-" + SEQUENCE.incrementAndGet();
    }

    private static long bookingIdOf(String allocationId) {
        return Math.abs(allocationId.hashCode()) + 1L;
    }
}
