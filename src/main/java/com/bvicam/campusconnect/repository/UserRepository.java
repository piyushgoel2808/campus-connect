package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Spring automatically implements this logic!
    Optional<User> findByEmail(String email);

    // Check if user exists before registering
    boolean existsByEmail(String email);
}