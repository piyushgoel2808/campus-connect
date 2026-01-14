package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.RegisterRequest;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.PostRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PostRepository postRepository; // ✅ Fixed: Repository is now available

    // ✅ Constructor Injection (Removes "Field injection not recommended" warning)
    @Autowired
    public AdminController(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           EmailService emailService,
                           PostRepository postRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.postRepository = postRepository;
    }

    // 1. Create User (Onboarding)
    @PostMapping("/create-user")
    public ResponseEntity<?> createUser(@RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Email already exists!");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setEnrollmentNumber(request.getEnrollmentNumber());

        // Default password logic
        String defaultPass = "Bvicam@2025";
        user.setPasswordHash(passwordEncoder.encode(defaultPass));
        user.setPasswordChanged(false);

        // ✅ Fixed: Saves Batch Year (Requires RegisterRequest to have getBatchYear())
        if (request.getBatchYear() != null) {
            user.setBatchYear(request.getBatchYear());
        }

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

    // 3. Broadcast Email
    @PostMapping("/broadcast")
    public ResponseEntity<?> sendBroadcast(@RequestBody Map<String, String> payload) {
        String subject = payload.get("subject");
        String body = payload.get("body");

        if (subject == null || body == null) {
            return ResponseEntity.badRequest().body("Subject and body are required");
        }

        emailService.sendBroadcastEmail(subject, body);
        return ResponseEntity.ok("Broadcast started in background!");
    }

    // 4. Moderate Content (Delete Any Post)
    @DeleteMapping("/delete-post/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        // ✅ Fixed: Now uses the injected postRepository
        if (!postRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        postRepository.deleteById(id);
        return ResponseEntity.ok("Post deleted by Admin.");
    }

    // 5. Update User (Robust Version)
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User updatedData) {
        System.out.println("📝 Admin attempting to update user ID: " + id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Update fields safely
        if (updatedData.getName() != null) user.setName(updatedData.getName());
        if (updatedData.getEmail() != null) user.setEmail(updatedData.getEmail());
        if (updatedData.getRole() != null) user.setRole(updatedData.getRole());

        // Update Batch Year
        if (updatedData.getBatchYear() != null) user.setBatchYear(updatedData.getBatchYear());

        // Update Professional details
        if (updatedData.getHeadline() != null) user.setHeadline(updatedData.getHeadline());
        if (updatedData.getSkills() != null) user.setSkills(updatedData.getSkills());
        if (updatedData.getCurrentCompany() != null) user.setCurrentCompany(updatedData.getCurrentCompany());

        try {
            userRepository.save(user);
            System.out.println("✅ User " + id + " updated successfully!");
            return ResponseEntity.ok("User updated successfully.");
        } catch (Exception e) {
            System.err.println("❌ Error saving user: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}