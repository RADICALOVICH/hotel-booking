package ru.hotelbooking.booking.user.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import ru.hotelbooking.booking.user.entity.User;
import ru.hotelbooking.booking.user.entity.UserRole;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role order by u.id")
    List<User> lockAllByRole(UserRole role);
}
