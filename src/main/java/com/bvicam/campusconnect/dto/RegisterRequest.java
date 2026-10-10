package com.bvicam.campusconnect.dto;

import com.bvicam.campusconnect.entity.Role;
import lombok.Data;

/**
 * Data Transfer Object (DTO) capturing user registration details submitted to {@code POST /api/auth/register}.
 *
 * <p><b>Architectural Role:</b></p>
 * <ul>
 *   <li>Carries core account credentials, academic affiliation ({@link #departmentCode}, {@link #batchYear},
 *       {@link #enrollmentNumber}), and optional initial professional profile details.</li>
 *   <li>Processed by {@code AuthController}, which hashes the plaintext {@link #password} using BCrypt,
 *       resolves the {@link com.bvicam.campusconnect.entity.Department} by {@link #departmentCode},
 *       and persists the new {@link com.bvicam.campusconnect.entity.User} entity.</li>
 * </ul>
 */
@Data
public class RegisterRequest {

    // =========================================================================
    // Core Account & Academic Fields
    // =========================================================================

    /** Full display name of the registering user. */
    private String name;

    /** Unique email address used for login and notifications. */
    private String email;

    /** Raw plaintext password to be hashed with BCrypt before storage. */
    private String password;

    /** Requested user role ({@code STUDENT} or {@code ALUMNI}; defaults to {@code STUDENT} if omitted). */
    private Role role; // STUDENT or ALUMNI

    /** University enrollment number (for students). */
    private String enrollmentNumber;

    /** Graduation or batch year (for alumni and students). */
    private Integer batchYear;

    /** Short code of the user's academic department (e.g., {@code "MCA"} or {@code "BJMC"}). */
    private String departmentCode;
    
    // =========================================================================
    // Optional Professional Profile Fields
    // =========================================================================

    // Professional Profile Fields
    /** Short bio or professional headline. */
    private String headline;

    /** Current employer or company name (primarily for Alumni). */
    private String currentCompany;

    /** Current job title or role. */
    private String designation;

    /** Comma-separated list of technical skills. */
    private String skills;

    /** Public GitHub profile link. */
    private String githubUrl;

    /** Public LinkedIn profile link. */
    private String linkedinUrl;

    /** Summary of prior internships or work experience. */
    private String pastExperience;
}