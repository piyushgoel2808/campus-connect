package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.AuthResponse;
import com.bvicam.campusconnect.dto.LoginRequest;
import com.bvicam.campusconnect.dto.RegisterRequest;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity; // Better for HTTP responses
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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
    private PasswordEncoder passwordEncoder;

    // 1. REGISTER
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // Validation Check
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Error: Email is already taken!");
        }

        // Create new User Entity from Request Data
        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setRole(request.getRole());
        user.setEnrollmentNumber(request.getEnrollmentNumber());

        // Encrypt Password
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        try {
            userRepository.save(user);
            return ResponseEntity.ok("User registered successfully!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Database Error: " + e.getMessage());
        }
    }

    // 2. LOGIN
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest authRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword())
            );
        } catch (Exception ex) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }

        // Generate Token
        String token = jwtUtil.generateToken(authRequest.getEmail());
        User user = userRepository.findByEmail(authRequest.getEmail()).orElseThrow();

        return ResponseEntity.ok(new AuthResponse(token, user.getName(), user.getRole()));
    }
    // 3. CHANGE PASSWORD (Forced or Voluntary)
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody java.util.Map<String, String> request) {
        String email = request.get("email");
        String newPassword = request.get("newPassword");

        User user = userRepository.findByEmail(email).orElseThrow();

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChanged(true); // Flag: Password is now secure

        userRepository.save(user);
        return ResponseEntity.ok("Password changed successfully!");
    }
}