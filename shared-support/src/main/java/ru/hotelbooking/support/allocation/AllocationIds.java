package ru.hotelbooking.support.allocation;

public final class AllocationIds {
    private AllocationIds() {}

    public static String forBookingRoom(long bookingId, long roomId) {
        return "booking-" + bookingId + "-room-" + roomId;
    }
}
