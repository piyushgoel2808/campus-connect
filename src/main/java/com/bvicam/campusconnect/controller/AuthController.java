package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.AuthResponse;
import com.bvicam.campusconnect.dto.LoginRequest;
import com.bvicam.campusconnect.dto.RegisterRequest;
import com.bvicam.campusconnect.entity.Department;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.repository.DepartmentRepository;
import com.bvicam.campusconnect.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * REGISTER NEW USER
     * Handles student and faculty registration with department linkage.
     */
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // 1. Validation: Check if email exists
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Error: Email is already taken!");
        }

        try {
            User user = new User();
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setEnrollmentNumber(request.getEnrollmentNumber());
            user.setBatchYear(request.getBatchYear());

            // Encode password before saving
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

            // Set Role: Default to STUDENT if not provided
            user.setRole(request.getRole() != null ? request.getRole() : Role.STUDENT);

            // 2. Link Department by Code
            if (request.getDepartmentCode() != null && !request.getDepartmentCode().isEmpty()) {
                Department dept = departmentRepository.findByCode(request.getDepartmentCode())
                        .orElseThrow(() -> new RuntimeException("Error: Department '" + request.getDepartmentCode() + "' not found."));
                user.setDepartment(dept);
            }

            userRepository.save(user);
            return ResponseEntity.ok("User registered successfully!");

        } catch (RuntimeException e) {
            // Catches Department not found or other business logic errors
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            // Catches unexpected database or server issues
            return ResponseEntity.internalServerError().body("Database Error: " + e.getMessage());
        }
    }

    /**
     * LOGIN USER
     * Authenticates credentials and returns a JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest authRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword())
            );

            String token = jwtUtil.generateToken(authRequest.getEmail());
            User user = userRepository.findByEmail(authRequest.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            return ResponseEntity.ok(new AuthResponse(token, user.getName(), user.getRole().name()));
        } catch (Exception ex) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }
    }

    /**
     * CHANGE PASSWORD
     * Updates user password and marks it as changed.
     */
    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String newPassword = request.get("newPassword");

        if (email == null || newPassword == null) {
            return ResponseEntity.badRequest().body("Email and new password are required.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChanged(true);

        userRepository.save(user);
        return ResponseEntity.ok("Password changed successfully!");
    }
}