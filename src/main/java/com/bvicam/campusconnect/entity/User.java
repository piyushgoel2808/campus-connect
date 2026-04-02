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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore // Security Fix: Never send passwords in JSON responses
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING) // Stores "STUDENT", "ADMIN" in DB
    private Role role;

    private String enrollmentNumber;
    private Integer batchYear;

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

    // --- RELATIONSHIPS WITH JSON FIXES ---

    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore // FIX: Prevents recursion when fetching chat history
    private List<ChatMessage> sentMessages = new ArrayList<>();

    @OneToMany(mappedBy = "recipient", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore // FIX: Prevents recursion when fetching chat history
    private List<ChatMessage> receivedMessages = new ArrayList<>();

    @ManyToMany(mappedBy = "participants")
    @JsonIgnore // Prevents Infinite JSON Recursion
    private Set<Event> events;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    // Helper method to get course code easily (e.g., user.getDepartmentCode())
    public String getDepartmentCode() {
        return department != null ? department.getCode() : "UNKNOWN";
    }

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