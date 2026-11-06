package ru.hotelbooking.booking.booking.client;

import java.util.List;

public record RecommendedRooms(List<RecommendedRoom> rooms, long totalCount) {}
