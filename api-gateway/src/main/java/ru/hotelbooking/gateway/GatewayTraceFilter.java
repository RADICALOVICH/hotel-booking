package ru.hotelbooking.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class GatewayTraceFilter implements WebFilter {
    static final String HEADER = "X-Trace-Id";
    private static final String ATTRIBUTE = GatewayTraceFilter.class.getName() + ".traceId";
    private static final Logger log = LoggerFactory.getLogger(GatewayTraceFilter.class);
    private static final Pattern FORMAT = Pattern.compile("[0-9a-fA-F]{32}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var supplied = exchange.getRequest().getHeaders().get(HEADER);
        String traceId =
                supplied != null
                                && supplied.size() == 1
                                && FORMAT.matcher(supplied.getFirst()).matches()
                        ? supplied.getFirst()
                        : UUID.randomUUID().toString().replace("-", "");
        exchange.getAttributes().put(ATTRIBUTE, traceId);
        exchange.getResponse()
                .beforeCommit(
                        () -> {
                            exchange.getResponse().getHeaders().set(HEADER, traceId);
                            return Mono.empty();
                        });
        var request =
                exchange.getRequest()
                        .mutate()
                        .headers(headers -> headers.set(HEADER, traceId))
                        .build();
        return chain.filter(exchange.mutate().request(request).build())
                .doOnSuccess(
                        ignored ->
                                log.info(
                                        "event=http_request traceId={} method={} path={} status={}",
                                        traceId,
                                        request.getMethod(),
                                        request.getPath().value(),
                                        exchange.getResponse().getStatusCode()));
    }

    static String traceId(ServerWebExchange exchange) {
        return exchange.getRequiredAttribute(ATTRIBUTE);
    }
}
