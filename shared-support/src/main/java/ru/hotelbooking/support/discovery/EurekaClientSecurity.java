package ru.hotelbooking.support.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.netflix.eureka.http.EurekaClientHttpRequestFactorySupplier;
import org.springframework.cloud.netflix.eureka.http.RestClientDiscoveryClientOptionalArgs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "eureka.client.enabled", matchIfMissing = true)
public class EurekaClientSecurity {
    @Bean
    RestClientDiscoveryClientOptionalArgs registryTransport(
            EurekaClientHttpRequestFactorySupplier requests,
            @Value("${EUREKA_USERNAME:}") String username,
            @Value("${EUREKA_PASSWORD:}") String password,
            @Value("${eureka.client.service-url.defaultZone}") String registryUrl) {
        if (username.isBlank() || username.contains(":") || password.isBlank()) {
            throw new IllegalStateException(
                    "Set non-blank EUREKA_USERNAME (without a colon) and EUREKA_PASSWORD");
        }
        if (!isPlainRegistryUrl(registryUrl)) {
            throw new IllegalStateException("EUREKA_URL must be an HTTP URL without credentials");
        }

        // Netflix can log failed endpoint URLs, so credentials belong only in the header.
        return new RestClientDiscoveryClientOptionalArgs(
                requests,
                () ->
                        RestClient.builder()
                                .defaultHeaders(
                                        headers ->
                                                headers.setBasicAuth(
                                                        username,
                                                        password,
                                                        StandardCharsets.UTF_8)));
    }

    private static boolean isPlainRegistryUrl(String url) {
        try {
            URI uri = URI.create(url);
            return ("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && !url.contains(",")
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
