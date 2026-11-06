package ru.hotelbooking.hotel.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class HotelService {
    private final HotelRepository hotels;

    public HotelService(HotelRepository hotels) {
        this.hotels = hotels;
    }

    @Transactional
    public HotelResponse create(CreateHotelRequest request) {
        Hotel hotel = hotels.save(new Hotel(request.name(), request.address()));
        return toResponse(hotel);
    }

    @Transactional(readOnly = true)
    public Optional<HotelResponse> find(long id) {
        return hotels.findById(id).map(HotelService::toResponse);
    }

    private static HotelResponse toResponse(Hotel hotel) {
        return new HotelResponse(hotel.getId(), hotel.getName(), hotel.getAddress());
    }
}
