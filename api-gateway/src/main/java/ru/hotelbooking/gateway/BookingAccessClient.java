package ru.hotelbooking.gateway;

import io.netty.channel.ChannelOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import ru.hotelbooking.support.trace.TraceIds;

import java.time.Duration;

@Component
class BookingAccessClient {
    private final WebClient booking;

    BookingAccessClient(
            WebClient.Builder builder,
            ReactorLoadBalancerExchangeFilterFunction loadBalancer,
            @Value("${gateway.booking.base-url}") String baseUrl) {
        var transport =
                HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500)
                        .responseTimeout(Duration.ofSeconds(1));
        this.booking =
                builder.baseUrl(baseUrl)
                        .clientConnector(new ReactorClientHttpConnector(transport))
                        .filter(loadBalancer)
                        .build();
    }

    Mono<Result> check(String token, String traceId) {
        return booking.get()
                .uri("/internal/auth/validate")
                .headers(
                        headers -> {
                            headers.setBearerAuth(token);
                            headers.set(TraceIds.HEADER, traceId);
                        })
                .exchangeToMono(
                        response ->
                                Mono.just(
                                        switch (response.statusCode().value()) {
                                            case 204 -> Result.CURRENT;
                                            case 401 -> Result.REVOKED;
                                            default -> Result.UNAVAILABLE;
                                        }))
                .timeout(Duration.ofMillis(1500))
                .onErrorReturn(Result.UNAVAILABLE);
    }

    enum Result {
        CURRENT,
        REVOKED,
        UNAVAILABLE
    }
}
