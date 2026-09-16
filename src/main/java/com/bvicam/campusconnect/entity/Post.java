package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 5000)
    private String content;

    private String imageUrl;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    private LocalDateTime createdAt;

    // Use Integer wrapper to prevent "null" errors during database operations
    @Column(nullable = false)
    private Integer likes = 0;

    // --- NEW FIELD: Pinning Logic ---
    @Column(nullable = false)
    private Boolean isPinned = false;

    // --- NEW FIELD: Comment Counter ---
    @Column(nullable = false)
    private Integer commentsCount = 0;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<Comment> comments = new java.util.ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.likes == null) {
            this.likes = 0;
        }
        // Ensure isPinned is never null in the database
        if (this.isPinned == null) {
            this.isPinned = false;
        }
        if (this.commentsCount == null) {
            this.commentsCount = 0;
        }
    }

    // --- CRITICAL FIX: Safe equals and hashCode ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Post post = (Post) o;
        return Objects.equals(id, post.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}