package ru.hotelbooking.hotel.support;

import ru.hotelbooking.hotel.security.client.AccessVerifier;
import ru.hotelbooking.hotel.security.exception.AccessCheckUnavailableException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Stands in for Booking's /internal/auth/validate in Hotel tests. */
public class StubAccessVerifier implements AccessVerifier {
    private final Set<String> revoked = ConcurrentHashMap.newKeySet();
    private final List<String> checked = new CopyOnWriteArrayList<>();
    private volatile boolean unavailable;

    @Override
    public boolean isCurrent(String token) {
        checked.add(token);
        if (unavailable) {
            throw new AccessCheckUnavailableException("Booking is down in this test");
        }
        return !revoked.contains(token);
    }

    public void revoke(String token) {
        revoked.add(token);
    }

    public void makeUnavailable() {
        unavailable = true;
    }

    public List<String> checkedTokens() {
        return List.copyOf(checked);
    }

    public void reset() {
        revoked.clear();
        checked.clear();
        unavailable = false;
    }
}
