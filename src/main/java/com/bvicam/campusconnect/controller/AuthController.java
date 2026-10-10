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

/**
 * REST Controller for Authentication and Account Security ({@code /api/auth}).
 * <p>
 * Exposes public endpoints (configured as {@code permitAll()} in {@code SecurityConfig})
 * for user registration, email/password authentication with JWT token issuance,
 * and password updates.
 * </p>
 */
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

    // =========================================================
    // 1. USER REGISTRATION
    // =========================================================

    /**
     * Registers a new student, alumni, or admin user and links their academic department.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/auth/register}</li>
     *   <li><b>Access Role:</b> Public ({@code permitAll})</li>
     *   <li><b>Request Payload:</b> {@link RegisterRequest} JSON object</li>
     *   <li><b>Response:</b> {@code 200 OK} on success, {@code 400 Bad Request} if email exists or department is invalid</li>
     * </ul>
     *
     * @param request the registration payload containing user credentials and academic details
     * @return a {@link ResponseEntity} with a success or error message
     */
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // 1. Validation: Check if email exists
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Error: Email is already taken!");
        }

        try {
            // 2. Construct new User entity with basic details
            User user = new User();
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setEnrollmentNumber(request.getEnrollmentNumber());
            user.setBatchYear(request.getBatchYear());

            // 3. Encode password using BCrypt before saving
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

            // 4. Set Role: Default to STUDENT if not provided
            user.setRole(request.getRole() != null ? request.getRole() : Role.STUDENT);

            // 5. Link Department by Code (e.g., "MCA", "BJMC")
            if (request.getDepartmentCode() != null && !request.getDepartmentCode().isEmpty()) {
                Department dept = departmentRepository.findByCode(request.getDepartmentCode())
                        .orElseThrow(() -> new RuntimeException("Error: Department '" + request.getDepartmentCode() + "' not found."));
                user.setDepartment(dept);
            }

            // 6. Persist the new user to MySQL
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

    // =========================================================
    // 2. USER LOGIN & JWT ISSUANCE
    // =========================================================

    /**
     * Authenticates a user's email and password and issues a signed JWT bearer token.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/auth/login}</li>
     *   <li><b>Access Role:</b> Public ({@code permitAll})</li>
     *   <li><b>Request Payload:</b> {@link LoginRequest} JSON object ({@code email}, {@code password})</li>
     *   <li><b>Response:</b> {@code 200 OK} with {@link AuthResponse} (JWT token, user ID, name, role),
     *       or {@code 401 Unauthorized} if credentials are invalid</li>
     * </ul>
     *
     * @param authRequest the login credentials
     * @return a {@link ResponseEntity} containing {@link AuthResponse} or a 401 error map
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest authRequest) {
        try {
            // Step 1: Delegate credential verification to Spring Security's AuthenticationManager
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword())
            );

            // Step 2: Generate a signed JWT token for the authenticated email
            String token = jwtUtil.generateToken(authRequest.getEmail());
            User user = userRepository.findByEmail(authRequest.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Step 3: Return the token and core user metadata to the frontend client
            return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getName(), user.getRole().name()));
        } catch (Exception ex) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password"));
        }
    }

    // =========================================================
    // 3. PASSWORD MANAGEMENT
    // =========================================================

    /**
     * Updates a user's password and marks {@code passwordChanged = true}.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/auth/change-password}</li>
     *   <li><b>Access Role:</b> Public / Authenticated User</li>
     *   <li><b>Request Payload:</b> JSON object containing {@code "email"} and {@code "newPassword"}</li>
     *   <li><b>Response:</b> {@code 200 OK} on success, or {@code 400 Bad Request} if fields are missing</li>
     * </ul>
     *
     * @param request map containing {@code email} and {@code newPassword}
     * @return a {@link ResponseEntity} confirming the password update
     */
    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request) {
        // Step 1: Extract and validate required parameters
        String email = request.get("email");
        String newPassword = request.get("newPassword");

        if (email == null || newPassword == null) {
            return ResponseEntity.badRequest().body("Email and new password are required.");
        }

        // Step 2: Locate user account by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Step 3: Hash the new password with BCrypt and flag password as changed
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChanged(true);

        userRepository.save(user);
        return ResponseEntity.ok("Password changed successfully!");
    }
}