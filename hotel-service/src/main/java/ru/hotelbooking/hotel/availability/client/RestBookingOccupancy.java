package ru.hotelbooking.hotel.availability.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import ru.hotelbooking.hotel.availability.exception.BookingOccupancyUnavailableException;
import ru.hotelbooking.hotel.common.client.BookingHttpClient;
import ru.hotelbooking.support.dates.BookedStay;
import ru.hotelbooking.support.security.OccupancyJwt;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class RestBookingOccupancy implements BookingOccupancy {
    private final BookingHttpClient booking;
    private final JwtEncoder tokens;
    private final Clock clock = Clock.systemUTC();

    public RestBookingOccupancy(
            BookingHttpClient booking, @Value("${OCCUPANCY_SHARED_SECRET:}") String secret) {
        this.booking = booking;
        tokens = OccupancyJwt.encoder(secret);
    }

    @Override
    public List<BookedStay> find(LocalDate startDate, LocalDate endDate) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return read(startDate, endDate);
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().is4xxClientError()) {
                    throw new BookingOccupancyUnavailableException(exception);
                }
                last = exception;
            } catch (RestClientException
                    | IllegalArgumentException
                    | NullPointerException exception) {
                last = exception;
            }
            if (attempt < 2) {
                try {
                    Thread.sleep(50L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new BookingOccupancyUnavailableException(interrupted);
                }
            }
        }
        throw new BookingOccupancyUnavailableException(last);
    }

    private List<BookedStay> read(LocalDate startDate, LocalDate endDate) {
        var result =
                booking.get(
                                OccupancyJwt.PATH,
                                OccupancyJwt.issue(tokens, clock),
                                Map.of("startDate", startDate, "endDate", endDate))
                        .retrieve()
                        .body(BookedStay[].class);
        if (result == null || Arrays.stream(result).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Empty occupancy response");
        }
        var stays = List.copyOf(Arrays.asList(result));
        if (stays.stream()
                .anyMatch(
                        stay ->
                                !stay.startDate().isBefore(endDate)
                                        || !stay.endDate().isAfter(startDate))) {
            throw new IllegalArgumentException(
                    "Occupancy response is outside the requested interval");
        }
        return stays;
    }
}
