package com.bvicam.campusconnect.entity;
import com.bvicam.campusconnect.entity.ChatMessage;
//import com.bvicam.campusconnect.dto.ChatMessage;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
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

    // ✅ FIXED: Changed 'boolean' to 'Boolean' to prevent JSON Error
// ✅ FIX: Rename to 'passwordChanged' (Remove 'is')
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
    // ... existing fields ...

    // ✅ FIX: Automatically delete user's data when user is deleted

    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> sentMessages = new ArrayList<>();

    @OneToMany(mappedBy = "recipient", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> receivedMessages = new ArrayList<>();

    // If you have a 'Post' entity for the job board/memory wall:
    // @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
    // private List<Post> posts = new ArrayList<>();
}