package ru.hotelbooking.hotel.availability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Body of confirm-availability and release; the room comes from the path. */
public record AllocationRequest(
        @NotNull @Positive Long bookingId,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9:._-]+") String allocationId,
        LocalDate startDate,
        LocalDate endDate) {}
