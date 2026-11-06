package ru.hotelbooking.booking.user;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
}
