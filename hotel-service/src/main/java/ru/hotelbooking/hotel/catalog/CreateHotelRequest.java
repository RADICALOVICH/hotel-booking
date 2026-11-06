package ru.hotelbooking.hotel.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateHotelRequest(
        @NotBlank @Size(max = 255) String name, @NotBlank @Size(max = 255) String address) {}
