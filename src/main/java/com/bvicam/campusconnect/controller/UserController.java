package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.UserProfileDto;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for User Profiles and the Campus Directory ({@code /api/users}).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return userRepository.findByEmail(principal.getName())
                .map(UserProfileDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<UserProfileDto> getAllUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            int safePage = Math.max(0, page != null ? page : 0);
            int safeSize = Math.min(200, Math.max(1, size != null ? size : 50));
            return userRepository.findAll(PageRequest.of(safePage, safeSize)).stream()
                    .map(UserProfileDto::fromEntity)
                    .toList();
        }
        return userRepository.findAll().stream()
                .map(UserProfileDto::fromEntity)
                .toList();
    }

    @GetMapping("/search")
    public List<UserProfileDto> searchDirectory(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer batch,
            @RequestParam(required = false) String q) {

        Role roleEnum = null;
        if (role != null && !role.trim().isEmpty() && !"ALL".equalsIgnoreCase(role.trim())) {
            try {
                roleEnum = Role.valueOf(role.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                roleEnum = null;
            }
        }

        String keyword = (q != null && !q.trim().isEmpty()) ? q.trim() : null;
        return userRepository.searchUsers(roleEnum, batch, keyword).stream()
                .map(UserProfileDto::fromEntity)
                .toList();
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UserProfileDto profileDto) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (profileDto == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile data is required."));
        }

        String linkedin = validateOptionalHttpUrl(profileDto.getLinkedinUrl(), "LinkedIn URL");
        String github = validateOptionalHttpUrl(profileDto.getGithubUrl(), "GitHub URL");

        String email = authentication.getName();
        return userRepository.findByEmail(email).map(user -> {
            log.debug("Updating profile for {}", email);

            if (profileDto.getHeadline() != null) user.setHeadline(profileDto.getHeadline().trim());
            if (profileDto.getCurrentCompany() != null) user.setCurrentCompany(profileDto.getCurrentCompany().trim());
            if (profileDto.getDesignation() != null) user.setDesignation(profileDto.getDesignation().trim());
            if (profileDto.getSkills() != null) user.setSkills(profileDto.getSkills().trim());
            if (profileDto.getPastExperience() != null) user.setPastExperience(profileDto.getPastExperience().trim());
            if (profileDto.getLinkedinUrl() != null) user.setLinkedinUrl(linkedin);
            if (profileDto.getGithubUrl() != null) user.setGithubUrl(github);
            if (profileDto.getNotifyMessages() != null) user.setNotifyMessages(profileDto.getNotifyMessages());
            if (profileDto.getNotifyEvents() != null) user.setNotifyEvents(profileDto.getNotifyEvents());
            if (profileDto.getNotifyJobs() != null) user.setNotifyJobs(profileDto.getNotifyJobs());

            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Profile Updated Successfully!"));
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found")));
    }

    private String validateOptionalHttpUrl(String rawUrl, String fieldName) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "";
        }
        String trimmed = rawUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return trimmed;
            }
        } catch (IllegalArgumentException ignored) {
        }
        throw new IllegalArgumentException(fieldName + " must start with http:// or https://");
    }
}
