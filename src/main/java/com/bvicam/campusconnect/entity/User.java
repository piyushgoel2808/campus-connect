package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data // Lombok generates Getters, Setters, ToString automatically
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    // Roles: STUDENT, ALUMNI, ADMIN
    @Column(nullable = false)
    private String role;

    // Specific to BVICAM
    private String enrollmentNumber;
    private Integer batchYear;

    // Security Flag
    private boolean isPasswordChanged = false; // Default is false

    // Profile Links (Optional)
    private String linkedinUrl;
    private String githubUrl;
    private String skills; // Stored as comma-separated string "Java,Python,SQL"
    // ... existing fields ...

    // ✅ NEW PROFESSIONAL FIELDS
    private String headline;       // e.g. "SDE II at Amazon"
    private String currentCompany; // e.g. "Amazon"
    private String designation;    // e.g. "Software Engineer"

    @Column(length = 2000)
    private String pastExperience; // e.g. "Intern at TCS (2020), Freelancer (2019)"

    // Existing fields we added earlier, ensuring they are there:
    // private String linkedinUrl;
    // private String githubUrl;
    // private String skills;
}