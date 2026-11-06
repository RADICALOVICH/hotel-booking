package ru.hotelbooking.support.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI serviceOpenApi(@Value("${hotel-booking.openapi.title}") String title) {
        return new OpenAPI()
                .info(new Info().title(title).version("0.1"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }

    @Bean
    OperationCustomizer describeAuthorization() {
        return (operation, handler) -> {
            PreAuthorize rule = handler.getMethodAnnotation(PreAuthorize.class);
            if (rule == null) {
                rule =
                        AnnotatedElementUtils.findMergedAnnotation(
                                handler.getBeanType(), PreAuthorize.class);
            }
            if (rule != null) {
                if (operation.getSecurity() == null) {
                    operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
                }
                operation.addExtension("x-authorization", rule.value());
            }
            return operation;
        };
    }
}
