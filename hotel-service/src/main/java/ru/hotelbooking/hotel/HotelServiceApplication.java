package ru.hotelbooking.hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import ru.hotelbooking.support.discovery.EurekaClientSecurity;
import ru.hotelbooking.support.openapi.OpenApiConfiguration;
import ru.hotelbooking.support.trace.TraceIdFilter;

@SpringBootApplication
@Import({EurekaClientSecurity.class, TraceIdFilter.class, OpenApiConfiguration.class})
public class HotelServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HotelServiceApplication.class, args);
    }
}
