package ru.hotelbooking.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import ru.hotelbooking.support.web.ErrorDTO;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
@Order(-2)
class GatewayErrors implements ErrorWebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayErrors.class);

    private final ObjectMapper json;

    GatewayErrors(ObjectMapper json) {
        this.json = json;
    }

    Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "A valid access token is required");
    }

    Mono<Void> forbidden(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied");
    }

    Mono<Void> badRequest(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid HTTP request");
    }

    Mono<Void> notFound(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.NOT_FOUND, "NOT_FOUND", "Route not found");
    }

    Mono<Void> authUnavailable(ServerWebExchange exchange) {
        return write(
                exchange,
                HttpStatus.SERVICE_UNAVAILABLE,
                "AUTH_SERVICE_UNAVAILABLE",
                "Access token validation is unavailable");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(failure);
        }
        HttpStatusCode status = status(failure);
        return switch (status.value()) {
            case 404 -> notFound(exchange);
            case 503 ->
                    write(exchange, status, "SERVICE_UNAVAILABLE", "Target service unavailable");
            case 504 ->
                    write(
                            exchange,
                            status,
                            "GATEWAY_TIMEOUT",
                            "Target service did not respond in time");
            default -> write(exchange, status, "HTTP_" + status.value(), "Request failed");
        };
    }

    private static HttpStatusCode status(Throwable failure) {
        if (failure instanceof ErrorResponse error) {
            return error.getStatusCode();
        }
        if (failure instanceof TimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (failure instanceof IOException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private Mono<Void> write(
            ServerWebExchange exchange, HttpStatusCode status, String code, String message) {
        var response = exchange.getResponse();
        String traceId = GatewayTraceFilter.traceId(exchange);
        String path = exchange.getRequest().getPath().value();
        byte[] bytes;
        try {
            bytes =
                    json.writeValueAsBytes(
                            new ErrorDTO(
                                    code, message, Instant.now(), path, Map.of(), null, traceId));
        } catch (JsonProcessingException failure) {
            return Mono.error(failure);
        }
        log.info(
                "event=gateway_error traceId={} status={} code={} path={}",
                traceId,
                status.value(),
                code,
                path);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
