package ru.hotelbooking.hotel.security.client;

/** Asks the token issuer whether a signed user token is still current. */
public interface AccessVerifier {
    /**
     * @return {@code false} when the token was revoked
     * @throws ru.hotelbooking.hotel.security.exception.AccessCheckUnavailableException when the
     *     answer cannot be obtained
     */
    boolean isCurrent(String token);
}
