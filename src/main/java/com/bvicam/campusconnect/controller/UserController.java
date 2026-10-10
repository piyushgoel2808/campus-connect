package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.UserProfileDto;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for User Profiles and the Campus Directory ({@code /api/users}).
 * <p>
 * Provides endpoints for fetching the currently logged-in user's profile, browsing and
 * searching the student/alumni directory via safe {@link UserProfileDto} projections
 * (which never expose password hashes), and updating professional profile fields and
 * notification preferences.
 * </p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    /**
     * Constructs the {@code UserController} using constructor-based dependency injection.
     *
     * @param userRepository repository for accessing {@link User} entities
     */
    // Best Practice: Constructor Injection instead of @Autowired on field
    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // =========================================================
    // 1. CURRENT USER PROFILE & DTO MAPPING
    // =========================================================

    /**
     * Retrieves the full profile of the currently authenticated user.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/users/me}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Response:</b> {@code 200 OK} with {@link User} entity, {@code 401 Unauthorized}, or {@code 404 Not Found}</li>
     * </ul>
     *
     * @param principal the authenticated user's security principal
     * @return a {@link ResponseEntity} containing the current user's record
     */
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

    /**
     * Converts a JPA {@link User} entity into a safe {@link UserProfileDto} for directory
     * and profile API responses.
     *
     * @param user the source {@link User} entity
     * @return a populated {@link UserProfileDto} without sensitive credentials
     */
    // Helper mapper
    private UserProfileDto mapToDto(User user) {
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole() != null ? user.getRole().name() : null);
        dto.setDepartmentName(user.getDepartment() != null ? user.getDepartment().getName() : null);
        dto.setDepartmentCode(user.getDepartment() != null ? user.getDepartment().getCode() : null);
        dto.setBatchYear(user.getBatchYear());
        dto.setEnrollmentNumber(user.getEnrollmentNumber());
        dto.setHeadline(user.getHeadline());
        dto.setCurrentCompany(user.getCurrentCompany());
        dto.setDesignation(user.getDesignation());
        dto.setSkills(user.getSkills());
        dto.setPastExperience(user.getPastExperience());
        dto.setLinkedinUrl(user.getLinkedinUrl());
        dto.setGithubUrl(user.getGithubUrl());
        dto.setNotifyMessages(user.getNotifyMessages());
        dto.setNotifyEvents(user.getNotifyEvents());
        dto.setNotifyJobs(user.getNotifyJobs());
        dto.setIsFlagged(user.getIsFlagged() != null ? user.getIsFlagged() : false);
        dto.setFlagCount(user.getFlagCount() != null ? user.getFlagCount() : 0);
        return dto;
    }

    // =========================================================
    // 2. CAMPUS DIRECTORY & SEARCH
    // =========================================================

    /**
     * Retrieves all users in the system mapped to {@link UserProfileDto} objects for the campus directory.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/users}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Response:</b> JSON array of {@link UserProfileDto} objects</li>
     * </ul>
     *
     * @return a list of all user profiles as DTOs
     */
    // 2. Get All Users (For Directory)
    @GetMapping
    public List<UserProfileDto> getAllUsers() {
        return userRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    /**
     * Searches the campus directory by optional role, graduation batch year, and free-text keyword.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/users/search?role={ROLE}&batch={YEAR}&q={KEYWORD}}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Query Parameters:</b>
     *     <ul>
     *       <li>{@code role} (optional) - {@code STUDENT}, {@code ALUMNI}, {@code ADMIN}, or {@code ALL}</li>
     *       <li>{@code batch} (optional) - graduation batch year (e.g., {@code 2025})</li>
     *       <li>{@code q} (optional) - keyword matching name, email, skills, company, headline, or designation</li>
     *     </ul>
     *   </li>
     *   <li><b>Response:</b> JSON array of matching {@link UserProfileDto} objects sorted alphabetically</li>
     * </ul>
     *
     * @param role  optional role filter string
     * @param batch optional batch year filter
     * @param q     optional search keyword
     * @return a filtered list of {@link UserProfileDto} objects
     */
    // 3. Search Directory
    @GetMapping("/search")
    public List<UserProfileDto> searchDirectory(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer batch,
            @RequestParam(required = false) String q) {

        // Step 1: Parse optional role parameter into Role enum (treating "ALL" or invalid values as null)
        Role roleEnum = null;
        if (role != null && !role.trim().isEmpty() && !"ALL".equalsIgnoreCase(role.trim())) {
            try {
                roleEnum = Role.valueOf(role.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                roleEnum = null;
            }
        }

        // Step 2: Normalize empty search strings to null so the JPQL query ignores the keyword filter
        // Handle empty search strings
        if (q != null && q.trim().isEmpty()) {
            q = null;
        } else if (q != null) {
            q = q.trim();
        }

        // Step 3: Execute multi-criteria search and map results to DTOs
        return userRepository.searchUsers(roleEnum, batch, q).stream().map(this::mapToDto).collect(Collectors.toList());
    }

    // =========================================================
    // 3. PROFILE & NOTIFICATION PREFERENCES UPDATE
    // =========================================================

    /**
     * Updates the authenticated user's professional profile (LinkedIn-style fields) and
     * in-app notification preferences. Only non-null fields in the DTO payload are modified.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code PUT /api/users/profile}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Request Payload:</b> {@link UserProfileDto} JSON body</li>
     *   <li><b>Response:</b> {@code 200 OK} on success, {@code 401 Unauthorized}, or {@code 404 Not Found}</li>
     * </ul>
     *
     * @param profileDto the {@link UserProfileDto} containing fields to update
     * @return a {@link ResponseEntity} confirming the profile update
     */
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
            if (profileDto.getNotifyMessages() != null) user.setNotifyMessages(profileDto.getNotifyMessages());
            if (profileDto.getNotifyEvents() != null) user.setNotifyEvents(profileDto.getNotifyEvents());
            if (profileDto.getNotifyJobs() != null) user.setNotifyJobs(profileDto.getNotifyJobs());

            userRepository.save(user);
            return ResponseEntity.ok("Profile Updated Successfully!");
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found"));
    }
}