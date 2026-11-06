package ru.hotelbooking.booking.user;

import java.util.UUID;

public record UserSession(UserResponse user, UUID accessVersion) {}
