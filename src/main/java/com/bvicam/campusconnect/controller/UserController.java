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
import java.util.stream.Collectors;

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

    // Helper mapper
    private UserProfileDto mapToDto(User user) {
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole() != null ? user.getRole().name() : null);
        dto.setDepartmentName(user.getDepartment() != null ? user.getDepartment().getName() : null);
        dto.setHeadline(user.getHeadline());
        dto.setCurrentCompany(user.getCurrentCompany());
        dto.setDesignation(user.getDesignation());
        dto.setSkills(user.getSkills());
        dto.setPastExperience(user.getPastExperience());
        dto.setLinkedinUrl(user.getLinkedinUrl());
        dto.setGithubUrl(user.getGithubUrl());
        return dto;
    }

    // 2. Get All Users (For Directory)
    @GetMapping
    public List<UserProfileDto> getAllUsers() {
        return userRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    // 3. Search Directory
    @GetMapping("/search")
    public List<UserProfileDto> searchDirectory(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer batch,
            @RequestParam(required = false) String q) {

        // Handle "ALL" as null to ignore the filter
        if ("ALL".equals(role)) role = null;
        // Handle empty search strings
        if (q != null && q.trim().isEmpty()) q = null;

        return userRepository.searchUsers(role, batch, q).stream().map(this::mapToDto).collect(Collectors.toList());
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