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

@Entity
@Table(name = "users")
@Getter // Replaces part of @Data
@Setter // Replaces part of @Data
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

    @Column(nullable = false)
    private String role;

    private String enrollmentNumber;
    private Integer batchYear;

    // Use Boolean (Wrapper) to handle nulls safely
    @Column(columnDefinition = "boolean default false")
    private Boolean passwordChanged = false;

    private String linkedinUrl;
    private String githubUrl;
    private String skills;

    private String headline;
    private String currentCompany;
    private String designation;

    @Column(length = 2000)
    private String pastExperience;

    // --- RELATIONSHIPS ---

    // Automatically delete user's data when user is deleted
    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> sentMessages = new ArrayList<>();

    @OneToMany(mappedBy = "recipient", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> receivedMessages = new ArrayList<>();

    @ManyToMany(mappedBy = "participants")
    @JsonIgnore // Prevents Infinite JSON Recursion
    private Set<Event> events;

    // --- CRITICAL FIX: Custom equals and hashCode (ONLY ID) ---
    // This prevents StackOverflowError when User is inside a Set/List in Event

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id); // Only check ID!
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // Only hash ID!
    }
}