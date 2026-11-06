package ru.hotelbooking.hotel.security.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ru.hotelbooking.hotel.security.exception.AccessCheckUnavailableException;

import java.time.Duration;

@Component
public class BookingAccessClient implements AccessVerifier {
    private final RestClient booking;

    public BookingAccessClient(
            RestClient.Builder builder,
            @Value("${hotel.booking.base-url}") String baseUrl,
            @Value("${hotel.booking.connect-timeout}") Duration connectTimeout,
            @Value("${hotel.booking.read-timeout}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(connectTimeout);
        requests.setReadTimeout(readTimeout);
        this.booking = builder.baseUrl(baseUrl).requestFactory(requests).build();
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
            return booking.get()
                    .uri("/internal/auth/validate")
                    .headers(headers -> headers.setBearerAuth(token))
                    .exchange((request, response) -> response.getStatusCode());
        } catch (RestClientException exception) {
            throw new AccessCheckUnavailableException("Booking is unreachable", exception);
        }
    }
}
