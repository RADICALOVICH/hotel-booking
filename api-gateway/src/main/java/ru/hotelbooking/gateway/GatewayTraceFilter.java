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

import ru.hotelbooking.support.trace.TraceIds;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class GatewayTraceFilter implements WebFilter {
    private static final String ATTRIBUTE = GatewayTraceFilter.class.getName() + ".traceId";
    private static final Logger log = LoggerFactory.getLogger(GatewayTraceFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId =
                TraceIds.fromHeaders(exchange.getRequest().getHeaders().get(TraceIds.HEADER));
        exchange.getAttributes().put(ATTRIBUTE, traceId);
        exchange.getResponse()
                .beforeCommit(
                        () -> {
                            exchange.getResponse().getHeaders().set(TraceIds.HEADER, traceId);
                            return Mono.empty();
                        });
        var request =
                exchange.getRequest()
                        .mutate()
                        .headers(headers -> headers.set(TraceIds.HEADER, traceId))
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
