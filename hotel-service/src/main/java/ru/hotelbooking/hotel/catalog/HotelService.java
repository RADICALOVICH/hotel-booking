package ru.hotelbooking.hotel.catalog;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    public List<HotelResponse> list() {
        return hotels.findAll(Sort.by("id")).stream().map(HotelService::toResponse).toList();
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
