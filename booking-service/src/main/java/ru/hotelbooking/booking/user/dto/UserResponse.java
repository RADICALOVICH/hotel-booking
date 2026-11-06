package ru.hotelbooking.booking.user.dto;

import ru.hotelbooking.booking.user.entity.UserRole;

public record UserResponse(long id, String username, UserRole role) {}
