package ru.hotelbooking.hotel.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String HEADER = "X-Trace-Id";
    private static final Pattern FORMAT = Pattern.compile("[0-9a-fA-F]{32}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders(HEADER));
        String traceId =
                headers.size() == 1 && FORMAT.matcher(headers.getFirst()).matches()
                        ? headers.getFirst()
                        : UUID.randomUUID().toString().replace("-", "");
        response.setHeader(HEADER, traceId);
        try (var scope = MDC.putCloseable("traceId", traceId)) {
            chain.doFilter(request, response);
            log.info(
                    "event=http_request method={} path={} status={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus());
        }
    }
}
