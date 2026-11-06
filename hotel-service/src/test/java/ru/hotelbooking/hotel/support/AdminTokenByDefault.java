package ru.hotelbooking.hotel.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;

/**
 * Catalog tests focus on catalog rules, so every MockMvc request carries an ADMIN token unless the
 * request sets its own Authorization header. Access rules are covered by HotelSecurityTest.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(HotelSecurityTestConfig.class)
public class AdminTokenByDefault {
    @Bean
    MockMvcBuilderCustomizer adminTokenByDefault(TestTokens tokens) {
        String header = "Bearer " + tokens.admin();
        return builder ->
                builder.defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, header));
    }
}
