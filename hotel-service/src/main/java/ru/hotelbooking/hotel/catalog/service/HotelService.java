package ru.hotelbooking.hotel.catalog.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hotelbooking.hotel.catalog.dto.CreateHotelRequest;
import ru.hotelbooking.hotel.catalog.dto.HotelResponse;
import ru.hotelbooking.hotel.catalog.dto.PatchHotelRequest;
import ru.hotelbooking.hotel.catalog.entity.Hotel;
import ru.hotelbooking.hotel.catalog.exception.HotelNotFoundException;
import ru.hotelbooking.hotel.catalog.repository.HotelRepository;

import java.util.Objects;

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
    public Page<HotelResponse> list(String name, String address, Pageable pageable) {
        return hotels.findByNameContainingIgnoreCaseAndAddressContainingIgnoreCase(
                        Objects.requireNonNullElse(name, "").strip(),
                        Objects.requireNonNullElse(address, "").strip(),
                        pageable)
                .map(HotelService::toResponse);
    }

    @Transactional(readOnly = true)
    public HotelResponse get(long id) {
        return hotels.findById(id)
                .map(HotelService::toResponse)
                .orElseThrow(() -> new HotelNotFoundException(id));
    }

    @Transactional
    public HotelResponse update(long id, PatchHotelRequest request) {
        Hotel hotel = findForUpdate(id);
        hotel.updateDetails(
                Objects.requireNonNullElse(request.name(), hotel.getName()),
                Objects.requireNonNullElse(request.address(), hotel.getAddress()));
        return toResponse(hotel);
    }

    @Transactional
    public void delete(long id) {
        hotels.delete(findForUpdate(id));
    }

    private Hotel findForUpdate(long id) {
        return hotels.findForUpdateById(id).orElseThrow(() -> new HotelNotFoundException(id));
    }

    private static HotelResponse toResponse(Hotel hotel) {
        return new HotelResponse(hotel.getId(), hotel.getName(), hotel.getAddress());
    }
}
