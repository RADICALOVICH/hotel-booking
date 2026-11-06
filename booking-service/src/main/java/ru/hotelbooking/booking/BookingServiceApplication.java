package ru.hotelbooking.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import ru.hotelbooking.support.discovery.EurekaClientSecurity;
import ru.hotelbooking.support.openapi.OpenApiConfiguration;
import ru.hotelbooking.support.trace.TraceIdFilter;

@SpringBootApplication
@EnableScheduling
@Import({EurekaClientSecurity.class, TraceIdFilter.class, OpenApiConfiguration.class})
public class BookingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
