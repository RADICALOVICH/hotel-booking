package ru.hotelbooking.booking.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders(TraceIds.HEADER));
        String traceId = TraceIds.fromHeader(headers.size() == 1 ? headers.getFirst() : null);
        response.setHeader(TraceIds.HEADER, traceId);
        try (var scope = TraceIds.open(traceId)) {
            chain.doFilter(request, response);
            log.info(
                    "event=http_request method={} path={} status={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus());
        }
    }
}
