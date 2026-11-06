package ru.hotelbooking.booking.booking.client;

public class HotelClientException extends RuntimeException {
    private final int status;
    private final String code;
    private final boolean ambiguous;
    private final boolean retryable;

    HotelClientException(int status, String code, boolean ambiguous, boolean retryable) {
        super("Hotel request failed (" + status + ", " + code + ")");
        this.status = status;
        this.code = code;
        this.ambiguous = ambiguous;
        this.retryable = retryable;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public boolean ambiguous() {
        return ambiguous;
    }

    boolean retryable() {
        return retryable;
    }
}
