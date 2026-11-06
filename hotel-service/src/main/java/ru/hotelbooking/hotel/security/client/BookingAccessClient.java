package ru.hotelbooking.hotel.security.client;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ru.hotelbooking.hotel.security.exception.AccessCheckUnavailableException;

import java.net.URI;
import java.time.Duration;

@Component
public class BookingAccessClient implements AccessVerifier {
    private final RestClient booking;
    private final LoadBalancerClient discovery;

    public BookingAccessClient(
            RestClient.Builder builder,
            @Nullable LoadBalancerClient discovery,
            @Value("${hotel.booking.base-url}") String baseUrl,
            @Value("${hotel.booking.connect-timeout}") Duration connectTimeout,
            @Value("${hotel.booking.read-timeout}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(connectTimeout);
        requests.setReadTimeout(readTimeout);
        this.booking =
                builder.baseUrl(baseUrl)
                        .requestFactory(requests)
                        .requestInitializer(
                                request -> {
                                    String traceId = MDC.get("traceId");
                                    if (traceId != null) {
                                        request.getHeaders().set("X-Trace-Id", traceId);
                                    }
                                })
                        .build();
        this.discovery = discovery;
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
                    .uri(uri -> resolve(uri.path("/internal/auth/validate").build()))
                    .headers(headers -> headers.setBearerAuth(token))
                    .exchange((request, response) -> response.getStatusCode());
        } catch (RestClientException exception) {
            throw new AccessCheckUnavailableException("Booking is unreachable", exception);
        }
    }

    private URI resolve(URI logicalUri) {
        if (discovery == null) {
            return logicalUri;
        }
        ServiceInstance instance = discovery.choose(logicalUri.getHost());
        if (instance == null) {
            throw new AccessCheckUnavailableException("Booking has no registered instance");
        }
        return discovery.reconstructURI(instance, logicalUri);
    }
}
