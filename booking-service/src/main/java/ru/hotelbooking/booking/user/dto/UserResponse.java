package ru.hotelbooking.booking.user.dto;

import ru.hotelbooking.booking.user.entity.User;
import ru.hotelbooking.booking.user.entity.UserRole;

public record UserResponse(long id, String username, UserRole role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}
