package ru.hotelbooking.booking.booking.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
class HotelCircuitConfiguration {
    static final String CONFIRM_BREAKER = "hotel-confirm";
    static final String RELEASE_BREAKER = "hotel-release";

    private static final Logger log = LoggerFactory.getLogger(HotelCircuitConfiguration.class);

    @Bean
    CircuitBreakerRegistry hotelCircuitBreakers() {
        var config =
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .ignoreException(
                                failure ->
                                        failure instanceof HotelClientException response
                                                && response.status() >= 400
                                                && response.status() < 500)
                        .build();
        var registry = CircuitBreakerRegistry.of(config);
        for (String name : List.of(CONFIRM_BREAKER, RELEASE_BREAKER)) {
            registry.circuitBreaker(name)
                    .getEventPublisher()
                    .onStateTransition(
                            event ->
                                    log.info(
                                            "event=hotel_circuit_transition circuit={} from={} to={}",
                                            name,
                                            event.getStateTransition().getFromState(),
                                            event.getStateTransition().getToState()));
        }
        return registry;
    }
}
