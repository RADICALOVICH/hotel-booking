package ru.hotelbooking.booking.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String missingUserHash;

    UserService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
        this.missingUserHash = passwords.encode("placeholder-credential");
    }

    public UserResponse register(String username, String password) {
        String login = normalizeUsername(username);
        validatePassword(password);
        if (users.existsByUsername(login)) {
            throw new UsernameAlreadyExistsException();
        }

        User user = new User(login, passwords.encode(password), UserRole.USER);
        return toResponse(saveRegistration(user));
    }

    public UserResponse authenticate(String username, String password) {
        String login = normalizeUsername(username);
        validatePassword(password);
        User user = users.findByUsername(login).orElse(null);
        String storedHash = user == null ? missingUserHash : user.passwordHash();
        if (!passwords.matches(password, storedHash) || user == null) {
            throw new InvalidCredentialsException();
        }
        return toResponse(user);
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
            throw new CredentialInputException("username", "Username is required");
        }
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64) {
            throw new CredentialInputException(
                    "username", "Username must be at most 64 characters");
        }
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new CredentialInputException("password", "Password is required");
        }
        int length = password.codePointCount(0, password.length());
        if (length < 12 || length > 128) {
            throw new CredentialInputException(
                    "password", "Password must be between 12 and 128 characters");
        }
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}
