package ru.hotelbooking.booking.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ru.hotelbooking.booking.user.dto.UserResponse;
import ru.hotelbooking.booking.user.dto.UserSession;
import ru.hotelbooking.booking.user.entity.User;
import ru.hotelbooking.booking.user.entity.UserRole;
import ru.hotelbooking.booking.user.exception.InvalidCredentialsException;
import ru.hotelbooking.booking.user.exception.UsernameAlreadyExistsException;
import ru.hotelbooking.booking.user.repository.UserRepository;

import java.util.UUID;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String missingUserHash;

    UserService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
        this.missingUserHash = passwords.encode("placeholder-credential");
    }

    public UserSession register(String username, String password) {
        return toSession(createAccount(username, password, UserRole.USER));
    }

    public User createAccount(String username, String password, UserRole role) {
        String login = UserInput.username(username);
        UserInput.password(password);
        if (users.existsByUsername(login)) {
            throw new UsernameAlreadyExistsException();
        }
        User user = save(new User(login, passwords.encode(password), role));
        log.info("event=user_created userId={} role={}", user.getId(), role);
        return user;
    }

    public UserSession authenticate(String username, String password) {
        String login = UserInput.username(username);
        UserInput.password(password);
        User user = users.findByUsername(login).orElse(null);
        String storedHash = user == null ? missingUserHash : user.getPasswordHash();
        if (!passwords.matches(password, storedHash) || user == null) {
            throw new InvalidCredentialsException();
        }
        return toSession(user);
    }

    public boolean hasCurrentAccess(long id, UUID version, String role) {
        return users.findById(id)
                .filter(
                        user ->
                                user.getAccessVersion().equals(version)
                                        && user.getRole().name().equals(role))
                .isPresent();
    }

    String hashPassword(String password) {
        UserInput.password(password);
        return passwords.encode(password);
    }

    private User save(User user) {
        try {
            return users.save(user);
        } catch (DataIntegrityViolationException exception) {
            if (users.existsByUsername(user.getUsername())) {
                throw new UsernameAlreadyExistsException(exception);
            }
            throw exception;
        }
    }

    private static UserSession toSession(User user) {
        return new UserSession(UserResponse.from(user), user.getAccessVersion());
    }
}
