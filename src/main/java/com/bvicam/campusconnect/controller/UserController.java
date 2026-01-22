package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.UserProfileDto;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    // Best Practice: Constructor Injection instead of @Autowired on field
    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 1. Get Current User Profile (Merged the two duplicates here)
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Principal principal) {
        // specific check to avoid NullPointerException if security context is empty
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return userRepository.findByEmail(principal.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 2. Get All Users (For Directory)
    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // 3. Search Directory
    @GetMapping("/search")
    public List<User> searchDirectory(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer batch,
            @RequestParam(required = false) String q) {

        // Handle "ALL" as null to ignore the filter
        if ("ALL".equals(role)) role = null;
        // Handle empty search strings
        if (q != null && q.trim().isEmpty()) q = null;

        return userRepository.searchUsers(role, batch, q);
    }

    // 4. Update Profile (LinkedIn Style)
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UserProfileDto profileDto) {
        // specific check to ensure authentication exists
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = authentication.getName();

        // Use a safer way to retrieve user than .orElseThrow() without arguments
        return userRepository.findByEmail(email).map(user -> {
            // Debugging
            System.out.println("Received Profile Update for: " + email);

            // Update fields from DTO only if they are not null
            if (profileDto.getHeadline() != null) user.setHeadline(profileDto.getHeadline());
            if (profileDto.getCurrentCompany() != null) user.setCurrentCompany(profileDto.getCurrentCompany());
            if (profileDto.getDesignation() != null) user.setDesignation(profileDto.getDesignation());
            if (profileDto.getSkills() != null) user.setSkills(profileDto.getSkills());
            if (profileDto.getPastExperience() != null) user.setPastExperience(profileDto.getPastExperience());
            if (profileDto.getLinkedinUrl() != null) user.setLinkedinUrl(profileDto.getLinkedinUrl());
            if (profileDto.getGithubUrl() != null) user.setGithubUrl(profileDto.getGithubUrl());

            userRepository.save(user);
            return ResponseEntity.ok("Profile Updated Successfully!");
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found"));
    }
}