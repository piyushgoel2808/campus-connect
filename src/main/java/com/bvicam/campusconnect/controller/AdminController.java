package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.RegisterRequest;
import com.bvicam.campusconnect.entity.Post;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.PostRepository; // ✅ Added Import
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PostRepository postRepository; // ✅ FIXED: Added this missing piece!

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

        String defaultPass = "Bvicam@2025";
        user.setPasswordHash(passwordEncoder.encode(defaultPass));
        user.setPasswordChanged(false);

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

        emailService.sendBroadcastEmail(subject, body);
        return ResponseEntity.ok("Broadcast started in background!");
    }

    // 4. Moderate Content (Delete Any Post)
    @DeleteMapping("/delete-post/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        if (!postRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        postRepository.deleteById(id);
        return ResponseEntity.ok("Post deleted by Admin.");
    }

    // 5. Edit Any User
// 5. Edit Any User (The critical missing link)
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User updatedData) {
        User user = userRepository.findById(id).orElseThrow();

        // Update ALL fields
        user.setName(updatedData.getName());
        user.setEmail(updatedData.getEmail());
        user.setRole(updatedData.getRole());
        user.setBatchYear(updatedData.getBatchYear());
        user.setHeadline(updatedData.getHeadline());
        user.setCurrentCompany(updatedData.getCurrentCompany());
        user.setSkills(updatedData.getSkills());

        userRepository.save(user);
        return ResponseEntity.ok("User updated successfully.");
    }
}