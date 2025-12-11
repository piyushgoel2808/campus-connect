package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.RegisterRequest;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 1. Create User (Onboarding)
    @PostMapping("/create-user")
    public ResponseEntity<?> createUser(@RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Email already exists!");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole()); // ADMIN sets the role (Student/Alumni)
        user.setEnrollmentNumber(request.getEnrollmentNumber());

        // SET DEFAULT PASSWORD
        String defaultPass = "Bvicam@2025";
        user.setPasswordHash(passwordEncoder.encode(defaultPass));
        user.setPasswordChanged(false); // Flag: Must change password later

        userRepository.save(user);
        return ResponseEntity.ok("User created with default password: " + defaultPass);
    }

    // 2. Delete User
    @DeleteMapping("/delete-user/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted successfully.");
    }
}