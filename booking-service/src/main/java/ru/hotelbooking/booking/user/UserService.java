package ru.hotelbooking.booking.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    UserService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public UserResponse register(String username, String password) {
        String login = normalizeUsername(username);
        validatePassword(password);
        if (users.existsByUsername(login)) {
            throw new UsernameAlreadyExistsException();
        }

        User user = new User(login, passwords.encode(password), UserRole.USER);
        User saved = saveRegistration(user);
        return new UserResponse(saved.getId(), saved.getUsername(), saved.getRole());
    }

    private User saveRegistration(User user) {
        try {
            return users.save(user);
        } catch (DataIntegrityViolationException exception) {
            if (users.existsByUsername(user.getUsername())) {
                throw new UsernameAlreadyExistsException(exception);
            }
            throw exception;
        }
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("Username must be at most 64 characters");
        }
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        int length = password.codePointCount(0, password.length());
        if (length < 12 || length > 128) {
            throw new IllegalArgumentException("Password must be between 12 and 128 characters");
        }
    }
}
