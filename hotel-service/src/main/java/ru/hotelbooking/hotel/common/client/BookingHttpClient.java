package ru.hotelbooking.hotel.common.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ru.hotelbooking.support.trace.TraceIds;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Common bounded transport for Hotel's authentication and occupancy reads. */
@Component
public class BookingHttpClient {
    private final RestClient booking;
    private final LoadBalancerClient discovery;

    public BookingHttpClient(
            RestClient.Builder builder,
            @Nullable LoadBalancerClient discovery,
            @Value("${hotel.booking.base-url}") String baseUrl,
            @Value("${hotel.booking.connect-timeout}") Duration connectTimeout,
            @Value("${hotel.booking.read-timeout}") Duration readTimeout) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(connectTimeout);
        requests.setReadTimeout(readTimeout);
        booking =
                builder.baseUrl(baseUrl)
                        .requestFactory(requests)
                        .requestInitializer(request -> TraceIds.propagate(request.getHeaders()))
                        .build();
        this.discovery = discovery;
    }

    public RestClient.RequestHeadersSpec<?> get(String path, String token, Map<String, ?> params) {
        return booking.get()
                .uri(
                        uri -> {
                            uri.path(path);
                            params.forEach(uri::queryParam);
                            return resolve(uri.build());
                        })
                .headers(headers -> headers.setBearerAuth(token));
    }

    private URI resolve(URI logical) {
        if (discovery == null) {
            return logical;
        }
        ServiceInstance instance = discovery.choose(logical.getHost());
        if (instance == null) {
            throw new org.springframework.web.client.ResourceAccessException(
                    "Booking has no registered instance");
        }
        return discovery.reconstructURI(instance, logical);
    }
}
