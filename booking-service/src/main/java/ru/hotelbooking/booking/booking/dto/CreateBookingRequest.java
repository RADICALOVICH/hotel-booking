package ru.hotelbooking.booking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBookingRequest(
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "79415aeb-5018-4c1a-8412-b977c05f3ff3",
                        description = "Idempotency key scoped to the authenticated user")
                UUID requestId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") Boolean autoSelect,
        @Schema(
                        example = "1",
                        description =
                                "Required for manual selection, ignored for automatic selection")
                Long roomId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-12-10")
                LocalDate startDate,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "2026-12-12",
                        description = "Exclusive checkout date")
                LocalDate endDate,
        @Schema(example = "1", description = "Optional hotel filter for automatic selection")
                Long hotelId) {
    public CreateBookingRequest {
        if (Boolean.TRUE.equals(autoSelect)) {
            roomId = null;
        }
    }
}
