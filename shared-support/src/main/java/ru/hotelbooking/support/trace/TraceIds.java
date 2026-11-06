package ru.hotelbooking.support.trace;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceIds {
    public static final String HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";
    private static final Pattern FORMAT = Pattern.compile("[0-9a-fA-F]{32}");

    private TraceIds() {}

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String fromHeader(String value) {
        return value != null && FORMAT.matcher(value).matches()
                ? value
                : UUID.randomUUID().toString().replace("-", "");
    }

    public static String fromHeaders(List<String> values) {
        return fromHeader(values != null && values.size() == 1 ? values.getFirst() : null);
    }

    public static void propagate(HttpHeaders headers) {
        String traceId = current();
        if (traceId != null) {
            headers.set(HEADER, traceId);
        }
    }

    public static Scope open(String traceId) {
        String previous = current();
        MDC.put(MDC_KEY, traceId);
        return () -> {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
