package ru.hotelbooking.hotel.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import ru.hotelbooking.hotel.security.client.AccessVerifier;
import ru.hotelbooking.hotel.security.config.HotelTokens;
import ru.hotelbooking.hotel.security.exception.AccessCheckUnavailableException;

import java.io.IOException;

/**
 * Runs after the signature check: a signed user token is accepted only while Booking confirms that
 * the account and its access version are unchanged.
 */
public class AccessVersionFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AccessVersionFilter.class);

    private final AccessVerifier verifier;
    private final SecurityErrorWriter errors;

    public AccessVersionFilter(AccessVerifier verifier, SecurityErrorWriter errors) {
        this.verifier = verifier;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication()
                        instanceof JwtAuthenticationToken authentication)
                || HotelTokens.isServiceToken(authentication.getToken())) {
            chain.doFilter(request, response);
            return;
        }
        try {
            if (verifier.isCurrent(authentication.getToken().getTokenValue())) {
                chain.doFilter(request, response);
                return;
            }
            log.info("event=user_token_revoked userId={}", authentication.getName());
            reject(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token was revoked");
        } catch (AccessCheckUnavailableException exception) {
            log.warn(
                    "event=access_check_unavailable userId={} reason=\"{}\"",
                    authentication.getName(),
                    exception.getMessage());
            reject(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_SERVICE_UNAVAILABLE",
                    "Access check is temporarily unavailable");
        }
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message)
            throws IOException {
        SecurityContextHolder.clearContext();
        errors.write(request, response, status, code, message);
    }
}
