package ru.hotelbooking.booking.booking.client;

import java.time.LocalDate;

public interface HotelClient {
    void confirm(long roomId, AllocationRequest request);

    void release(long roomId, AllocationRequest request);

    void releaseOnce(long roomId, AllocationRequest request);

    RecommendedRooms recommend(
            LocalDate startDate,
            LocalDate endDate,
            Long hotelId,
            int page,
            int size,
            String userToken);
}
