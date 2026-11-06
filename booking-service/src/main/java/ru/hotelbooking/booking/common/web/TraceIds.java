package ru.hotelbooking.booking.common.web;

import org.slf4j.MDC;

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
