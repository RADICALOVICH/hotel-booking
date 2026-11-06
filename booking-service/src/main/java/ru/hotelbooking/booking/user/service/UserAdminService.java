package ru.hotelbooking.booking.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hotelbooking.booking.booking.repository.BookingRepository;
import ru.hotelbooking.booking.user.dto.CreateUserRequest;
import ru.hotelbooking.booking.user.dto.PatchUserRequest;
import ru.hotelbooking.booking.user.dto.UserResponse;
import ru.hotelbooking.booking.user.entity.User;
import ru.hotelbooking.booking.user.entity.UserRole;
import ru.hotelbooking.booking.user.exception.LastAdminException;
import ru.hotelbooking.booking.user.exception.UserHasBookingsException;
import ru.hotelbooking.booking.user.exception.UserNotFoundException;
import ru.hotelbooking.booking.user.exception.UsernameAlreadyExistsException;
import ru.hotelbooking.booking.user.repository.UserRepository;

@Service
public class UserAdminService {
    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserRepository users;
    private final UserService accounts;
    private final BookingRepository bookings;

    UserAdminService(UserRepository users, UserService accounts, BookingRepository bookings) {
        this.users = users;
        this.accounts = accounts;
        this.bookings = bookings;
    }

    public UserResponse create(CreateUserRequest request) {
        UserRole role = UserInput.role(request.role());
        return UserResponse.from(
                accounts.createAccount(request.username(), request.password(), role));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return users.findAll(pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(long id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public UserResponse update(long id, PatchUserRequest patch) {
        User user = find(id);
        if (patch.username() != null) {
            rename(user, UserInput.username(patch.username()));
        }
        if (patch.password() != null) {
            user.changePasswordHash(accounts.hashPassword(patch.password()));
        }
        if (patch.role() != null) {
            changeRole(user, UserInput.role(patch.role()));
        }
        log.info("event=user_updated userId={} role={}", id, user.getRole());
        return UserResponse.from(user);
    }

    @Transactional
    public void delete(long id) {
        User user = find(id);
        if (bookings.existsByUserId(id)) {
            throw new UserHasBookingsException(id);
        }
        if (user.getRole() == UserRole.ADMIN) {
            requireAnotherAdmin(user);
        }
        try {
            users.delete(user);
            users.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new UserHasBookingsException(id);
        }
        log.info("event=user_deleted userId={}", id);
    }

    private void rename(User user, String username) {
        if (users.existsByUsernameAndIdNot(username, user.getId())) {
            throw new UsernameAlreadyExistsException();
        }
        user.changeUsername(username);
        try {
            users.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new UsernameAlreadyExistsException(exception);
        }
    }

    private void changeRole(User user, UserRole role) {
        if (user.getRole() == UserRole.ADMIN && role != UserRole.ADMIN) {
            requireAnotherAdmin(user);
        }
        user.changeRole(role);
    }

    // Locks every ADMIN row, so two concurrent demotions cannot both see "another admin".
    private void requireAnotherAdmin(User user) {
        boolean anotherAdmin =
                users.lockAllByRole(UserRole.ADMIN).stream()
                        .anyMatch(admin -> !admin.getId().equals(user.getId()));
        if (!anotherAdmin) {
            throw new LastAdminException();
        }
    }

    private User find(long id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}
