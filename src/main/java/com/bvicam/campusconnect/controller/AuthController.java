package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.AuthResponse;
import com.bvicam.campusconnect.dto.LoginRequest;
import com.bvicam.campusconnect.dto.RegisterRequest;
import com.bvicam.campusconnect.entity.Department;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.DepartmentRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * REST Controller for Authentication and Account Security ({@code /api/auth}).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Registration payload is required."));
        }

        String name = request.getName() != null ? request.getName().trim() : "";
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase(Locale.ROOT) : "";
        String rawPassword = request.getPassword() != null ? request.getPassword() : "";

        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Full name is required."));
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid email address is required."));
        }
        if (rawPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters long."));
        }

        // Prevent privilege escalation via self-registration
        Role requestedRole = request.getRole() != null ? request.getRole() : Role.STUDENT;
        if (requestedRole == Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Self-registration as ADMIN is not allowed."));
        }

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered."));
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setEnrollmentNumber(request.getEnrollmentNumber() != null ? request.getEnrollmentNumber().trim() : null);
        user.setBatchYear(request.getBatchYear());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordChanged(true);
        user.setRole(requestedRole);
        user.setHeadline(request.getHeadline());
        user.setCurrentCompany(request.getCurrentCompany());
        user.setDesignation(request.getDesignation());
        user.setSkills(request.getSkills());
        user.setGithubUrl(request.getGithubUrl());
        user.setLinkedinUrl(request.getLinkedinUrl());
        user.setPastExperience(request.getPastExperience());

        if (request.getDepartmentCode() != null && !request.getDepartmentCode().trim().isEmpty()) {
            Department dept = departmentRepository.findByCode(request.getDepartmentCode().trim())
                    .orElseThrow(() -> new IllegalArgumentException("Department '" + request.getDepartmentCode() + "' not found."));
            user.setDepartment(dept);
        }

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "User registered successfully!"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest authRequest) {
        if (authRequest == null || authRequest.getEmail() == null || authRequest.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required"));
        }

        String email = authRequest.getEmail().trim().toLowerCase(Locale.ROOT);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, authRequest.getPassword())
            );

            String token = jwtUtil.generateToken(email);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            boolean pwdChanged = user.getPasswordChanged() == null || user.getPasswordChanged();
            return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getName(), user.getRole().name(), pwdChanged));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid email or password"));
        }
    }

    /**
     * Changes the currently authenticated user's password after verifying their current password.
     */
    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request, Principal principal) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required."));
        }
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required."));
        }

        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");

        if (currentPassword == null || currentPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is required."));
        }
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 6 characters long."));
        }

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect."));
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChanged(true);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully!"));
    }
}
