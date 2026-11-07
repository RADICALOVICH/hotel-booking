package ru.hotelbooking.hotel.security.client;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import ru.hotelbooking.hotel.common.client.BookingHttpClient;
import ru.hotelbooking.hotel.security.exception.AccessCheckUnavailableException;

import java.util.Map;

@Component
public class BookingAccessClient implements AccessVerifier {
    private final BookingHttpClient booking;

    public BookingAccessClient(BookingHttpClient booking) {
        this.booking = booking;
    }

    @Override
    public boolean isCurrent(String token) {
        HttpStatusCode status = validate(token);
        if (status.value() == 204) {
            return true;
        }
        if (status.value() == 401) {
            return false;
        }
        throw new AccessCheckUnavailableException("Booking answered " + status.value());
    }

    private HttpStatusCode validate(String token) {
        try {
            return booking.get("/internal/auth/validate", token, Map.of())
                    .exchange((request, response) -> response.getStatusCode());
        } catch (RestClientException exception) {
            throw new AccessCheckUnavailableException("Booking is unreachable", exception);
        }
    }
}
