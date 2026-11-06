package ru.hotelbooking.booking.user.dto;

import java.util.UUID;

public record UserSession(UserResponse user, UUID accessVersion) {}
