package ru.hotelbooking.booking.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ru.hotelbooking.booking.user.entity.UserRole;
import ru.hotelbooking.booking.user.exception.UsernameAlreadyExistsException;
import ru.hotelbooking.booking.user.service.UserService;

// Runs after all beans exist but before the web server accepts requests,
// so the first login cannot arrive before the ADMIN account is stored.
@Component
public class InitialAdminInitializer implements SmartInitializingSingleton {
    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);

    private final UserService users;
    private final String username;
    private final String password;

    InitialAdminInitializer(
            UserService users,
            @Value("${booking.initial-admin.username}") String username,
            @Value("${booking.initial-admin.password}") String password) {
        this.users = users;
        this.username = username;
        this.password = password;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (username.isBlank() || password.isBlank()) {
            log.warn(
                    "event=initial_admin_skipped reason=not_configured"
                            + " hint=set BOOKING_ADMIN_USERNAME and BOOKING_ADMIN_PASSWORD");
            return;
        }
        try {
            users.createAccount(username, password, UserRole.ADMIN);
        } catch (UsernameAlreadyExistsException exception) {
            log.info("event=initial_admin_skipped reason=already_exists");
        }
    }
}
