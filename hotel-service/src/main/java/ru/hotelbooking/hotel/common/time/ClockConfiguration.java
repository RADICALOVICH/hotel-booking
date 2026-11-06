package ru.hotelbooking.hotel.common.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.hotelbooking.support.dates.StayDateRules;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class ClockConfiguration {
    /** Stay dates are business dates in the hotel's time zone, not in the server's zone. */
    @Bean
    Clock hotelClock() {
        return Clock.system(StayDateRules.BUSINESS_ZONE);
    }
}
