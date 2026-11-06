package ru.hotelbooking.booking.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.hotelbooking.booking.user.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);

    Optional<User> findByUsername(String username);
}
