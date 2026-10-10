package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA Entity representing a post published on the Campus Wall feed.
 *
 * <p><b>Database Table &amp; Relationships:</b></p>
 * <ul>
 *   <li>Maps to the {@code posts} table in MySQL.</li>
 *   <li>{@code author} ({@link ManyToOne}): Links the post to the {@link User} who created it
 *       via the {@code author_id} foreign key.</li>
 *   <li>{@code comments} ({@link OneToMany}): Bidirectional one-to-many relationship with {@link Comment}.
 *       Annotated with {@code @JsonIgnore} so fetching the wall feed does not eagerly serialize
 *       entire comment trees for every post (comments are fetched on demand via {@code /api/posts/{id}/comments}
 *       while {@link #commentsCount} provides the badge count immediately).</li>
 *   <li>Supports post liking ({@link #likes}) and admin/author pinning ({@link #isPinned}) so important
 *       announcements stay at the top of the feed.</li>
 * </ul>
 */
@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    // =========================================================================
    // Core Post Attributes
    // =========================================================================

    /** Auto-incremented primary key uniquely identifying this wall post. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Text body of the post (supports long-form updates up to 5,000 characters). */
    @Column(length = 5000)
    private String content;

    /** Optional relative URL (e.g., {@code /uploads/<uuid>_filename.jpg}) if the post includes an uploaded image. */
    private String imageUrl;

    /** The user who authored and published this post. */
    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    /** Timestamp when the post was created (populated automatically in {@link #onCreate()}). */
    private LocalDateTime createdAt;

    // =========================================================================
    // Engagement Metrics & Moderation Flags
    // =========================================================================

    // Use Integer wrapper to prevent "null" errors during database operations
    @Column(nullable = false)
    private Integer likes = 0;

    // --- NEW FIELD: Pinning Logic (Pinned posts sort first on the Campus Wall) ---
    @Column(nullable = false)
    private Boolean isPinned = false;

    // --- NEW FIELD: Comment Counter (Denormalized count for fast feed rendering) ---
    @Column(nullable = false)
    private Integer commentsCount = 0;

    // =========================================================================
    // Relationships & Lifecycle Callbacks
    // =========================================================================

    /**
     * All comments associated with this post.
     * {@code orphanRemoval = true} ensures deleting a post automatically deletes all its comments.
     * {@code @JsonIgnore} prevents N+1 queries and circular JSON serialization when listing posts.
     */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<Comment> comments = new java.util.ArrayList<>();

    /**
     * JPA lifecycle hook executed prior to inserting a new post row into the database.
     * Guarantees non-null defaults for {@code createdAt}, {@code likes}, {@code isPinned}, and {@code commentsCount}.
     */
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

    // =========================================================================
    // Entity Identity (ID-Only equals & hashCode)
    // =========================================================================

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