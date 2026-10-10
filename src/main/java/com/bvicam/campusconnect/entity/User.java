package com.bvicam.campusconnect.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Core JPA Entity representing a registered user (Student, Alumnus, or Administrator) in Campus Connect.
 *
 * <p><b>Database Table &amp; Key Relationships:</b></p>
 * <ul>
 *   <li>Maps to the {@code users} table in MySQL with a unique constraint on {@link #email}.</li>
 *   <li>{@code department} ({@link ManyToOne}): Associates the user with an academic {@link Department}
 *       (e.g., MCA, BJMC).</li>
 *   <li>{@code events} ({@link ManyToMany}): Inverse side of {@link Event#getParticipants()}; annotated
 *       with {@link JsonIgnore} to prevent infinite recursion when serializing events or users.</li>
 *   <li>{@code sentMessages} / {@code receivedMessages} ({@link OneToMany}): Inverse associations to
 *       {@link ChatMessage}, also marked {@link JsonIgnore}.</li>
 *   <li>Includes professional portfolio fields (company, designation, skills, LinkedIn/GitHub URLs),
 *       granular notification opt-in flags, and community moderation/flagging counters.</li>
 * </ul>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    // =========================================================================
    // Identity & Authentication Fields
    // =========================================================================

    /** Auto-incremented primary key uniquely identifying this user. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique institutional or personal email address used as the login username and JWT subject. */
    @Column(unique = true, nullable = false)
    private String email;

    /**
     * BCrypt-hashed password string.
     * Marked {@link JsonIgnore} so password hashes are never leaked in any REST API JSON response.
     */
    @JsonIgnore // Security Fix: Never send passwords in JSON responses
    @Column(nullable = false)
    private String passwordHash;

    /** Full display name of the user. */
    @Column(nullable = false)
    private String name;

    /** Authorization role ({@link Role#STUDENT}, {@link Role#ALUMNI}, or {@link Role#ADMIN}). */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING) // Stores "STUDENT", "ADMIN" in DB
    private Role role;

    // =========================================================================
    // Academic & Onboarding Metadata
    // =========================================================================

    /** University enrollment number (primarily applicable to {@code STUDENT} accounts). */
    private String enrollmentNumber;

    /** Graduation or batch year (primarily applicable to {@code ALUMNI} and {@code STUDENT} accounts). */
    private Integer batchYear;

    /** Tracks whether a bulk-imported user has changed their temporary default password. */
    @Column(columnDefinition = "boolean default false")
    private Boolean passwordChanged = false;

    // =========================================================================
    // Professional Portfolio & Social Links
    // =========================================================================

    /** Public LinkedIn profile URL. */
    private String linkedinUrl;

    /** Public GitHub profile URL. */
    private String githubUrl;

    /** Comma-separated list of technical or professional skills (e.g., "Java, Spring Boot, AWS"). */
    private String skills;

    /** Short professional bio or headline shown on profile cards. */
    private String headline;

    /** Current employer or organization (primarily for Alumni directory filtering). */
    private String currentCompany;

    /** Current job title or role at {@link #currentCompany}. */
    private String designation;

    /** Detailed work history or internship summary (up to 2,000 characters). */
    @Column(length = 2000)
    private String pastExperience;

    // =========================================================================
    // Notification Preferences & Moderation State
    // =========================================================================

    /** Whether the user wants to receive notifications for new direct messages. */
    @Column(nullable = false)
    private Boolean notifyMessages = true;

    /** Whether the user wants to receive notifications for new campus events. */
    @Column(nullable = false)
    private Boolean notifyEvents = true;

    /** Whether the user wants to receive notifications for new job postings. */
    @Column(nullable = false)
    private Boolean notifyJobs = true;

    /** Indicates whether this account has been flagged for admin review due to user reports. */
    @Column(nullable = false)
    private Boolean isFlagged = false;

    /** Total number of times other users have reported/flagged this account. */
    @Column(nullable = false)
    private Integer flagCount = 0;

    // =========================================================================
    // JPA Relationships (With @JsonIgnore Recursion Guards)
    // =========================================================================

    // --- RELATIONSHIPS WITH JSON FIXES ---

    /** Messages sent by this user; ignored in JSON to avoid circular serialization and lazy-loading errors. */
    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore // FIX: Prevents recursion when fetching chat history
    private List<ChatMessage> sentMessages = new ArrayList<>();

    /** Messages received by this user; ignored in JSON to avoid circular serialization and lazy-loading errors. */
    @OneToMany(mappedBy = "recipient", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore // FIX: Prevents recursion when fetching chat history
    private List<ChatMessage> receivedMessages = new ArrayList<>();

    /** Events this user has RSVP'd to; ignored in JSON to prevent infinite User <-> Event loops. */
    @ManyToMany(mappedBy = "participants")
    @JsonIgnore // Prevents Infinite JSON Recursion
    private Set<Event> events;

    /** Academic department this user belongs to (nullable for global Admin accounts). */
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    // =========================================================================
    // Helper Methods & JPA Lifecycle Callbacks
    // =========================================================================

    /**
     * Helper method to safely retrieve the user's short department code (e.g., {@code "MCA"})
     * without risking a {@link NullPointerException} when {@link #department} is unassigned.
     *
     * @return the department code string, or {@code "UNKNOWN"} if no department is linked
     */
    // Helper method to get course code easily (e.g., user.getDepartmentCode())
    public String getDepartmentCode() {
        return department != null ? department.getCode() : "UNKNOWN";
    }

    /**
     * JPA lifecycle callback executed before inserting a new user row to guarantee non-null
     * moderation fields ({@code isFlagged} and {@code flagCount}).
     */
    @PrePersist
    protected void onCreate() {
        if (this.isFlagged == null) {
            this.isFlagged = false;
        }
        if (this.flagCount == null) {
            this.flagCount = 0;
        }
    }

    // =========================================================================
    // Entity Identity (ID-Only equals & hashCode)
    // =========================================================================

    // --- CRITICAL FIX: Custom equals and hashCode ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}