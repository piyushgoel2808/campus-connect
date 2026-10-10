package com.bvicam.campusconnect.dto;

import lombok.Data;

/**
 * Data Transfer Object (DTO) representing a complete, client-safe view of a {@link com.bvicam.campusconnect.entity.User} profile.
 *
 * <p><b>Architectural Role:</b></p>
 * <ul>
 *   <li>Used by {@code UserController} to return profile details (and accept profile/preference updates)
 *       without exposing sensitive entity internals or triggering lazy-loaded JPA collections.</li>
 *   <li>Flattens the nested {@link com.bvicam.campusconnect.entity.Department} association into
 *       {@link #departmentName} and {@link #departmentCode} for convenient rendering in directory cards
 *       and profile modals.</li>
 * </ul>
 */
@Data
public class UserProfileDto {

    // =========================================================================
    // Core Identity & Academic Affiliation
    // =========================================================================

    /** Unique database ID of the user. */
    private Long id;

    /** Full display name of the user. */
    private String name;

    /** Registered email address of the user. */
    private String email;

    /** Role name ({@code "STUDENT"}, {@code "ALUMNI"}, or {@code "ADMIN"}). */
    private String role;

    /** Full name of the user's academic department (e.g., "Master of Computer Applications"). */
    private String departmentName;

    /** Short code of the user's academic department (e.g., "MCA"). */
    private String departmentCode;

    /** Graduation or batch year. */
    private Integer batchYear;

    /** University enrollment number (applicable to students). */
    private String enrollmentNumber;

    // =========================================================================
    // Professional Portfolio & Social Links
    // =========================================================================

    /** Short professional headline or bio. */
    private String headline;

    /** Current company or organization name. */
    private String currentCompany;

    /** Current job title or designation. */
    private String designation;

    /** Comma-separated list of technical or domain skills. */
    private String skills;

    /** Summary of past work experience or internships. */
    private String pastExperience;

    /** Public LinkedIn profile URL. */
    private String linkedinUrl;

    /** Public GitHub profile URL. */
    private String githubUrl;

    // =========================================================================
    // Notification Preferences & Moderation Metadata
    // =========================================================================

    /** Preference flag: receive notifications for new direct messages. */
    private Boolean notifyMessages;

    /** Preference flag: receive notifications for new campus events. */
    private Boolean notifyEvents;

    /** Preference flag: receive notifications for new job postings. */
    private Boolean notifyJobs;

    /** Whether this user profile has been flagged for moderator review. */
    private Boolean isFlagged;

    /** Number of community flags/reports filed against this profile. */
    private Integer flagCount;
}