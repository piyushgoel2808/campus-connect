package com.bvicam.campusconnect.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA Entity representing a user comment (or nested reply) on a Campus Wall {@link Post}.
 *
 * <p><b>Database Table &amp; Relationships:</b></p>
 * <ul>
 *   <li>Maps to the {@code comments} table in MySQL.</li>
 *   <li>{@code author} ({@link ManyToOne}, {@code EAGER}): The {@link User} who wrote the comment,
 *       fetched eagerly so author metadata is immediately available when rendering comment trees.</li>
 *   <li>{@code post} ({@link ManyToOne}, {@code LAZY}, {@link JsonIgnore}): The parent {@link Post}
 *       this comment belongs to. Marked {@code @JsonIgnore} to prevent infinite bidirectional JSON recursion.</li>
 *   <li>{@code parentComment} &amp; {@code replies}: Self-referential tree structure supporting nested
 *       threaded replies ordered chronologically ({@code createdAt ASC}). Deleting a parent comment
 *       cascades to all its nested replies ({@code cascade = CascadeType.ALL, orphanRemoval = true}).</li>
 * </ul>
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    // =========================================================================
    // Core Fields
    // =========================================================================

    /** Auto-incremented primary key uniquely identifying this comment. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Text body of the comment (required, up to 2,000 characters). */
    @Column(nullable = false, length = 2000)
    private String content;

    /** Timestamp when the comment was created (automatically initialized via {@link #onCreate()}). */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // =========================================================================
    // JPA Relationships & Threaded Reply Hierarchy
    // =========================================================================

    /** The user who authored this comment. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /**
     * The wall post on which this comment was made.
     * Ignored during JSON serialization to avoid circular {@code Post -> Comment -> Post} serialization loops.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    @JsonIgnore
    private Post post;

    /**
     * Optional parent comment when this comment is a nested reply; {@code null} for top-level comments.
     * Ignored directly in JSON (exposed instead as {@code parentCommentId} via {@link #getParentCommentId()})
     * to prevent infinite parent-child recursion.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnore
    private Comment parentComment;

    /** Child replies to this comment, ordered oldest-first and automatically removed if this comment is deleted. */
    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private java.util.List<Comment> replies = new java.util.ArrayList<>();

    // =========================================================================
    // JSON Computed Properties & Lifecycle Hooks
    // =========================================================================

    /**
     * Exposes {@code parentCommentId} in the serialized JSON payload so the frontend can identify
     * whether this comment is a reply without serializing the entire {@link #parentComment} object graph.
     *
     * @return the parent comment's ID, or {@code null} if this is a top-level comment
     */
    public Long getParentCommentId() {
        return parentComment != null ? parentComment.getId() : null;
    }

    /**
     * JPA lifecycle callback invoked prior to SQL {@code INSERT} to ensure {@code createdAt} is populated.
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // =========================================================================
    // Entity Identity (ID-Only equals & hashCode)
    // =========================================================================

    /**
     * Compares comments strictly by database primary key ({@code id}) to avoid triggering lazy-loading
     * proxies or infinite recursion across bidirectional associations.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Comment comment = (Comment) o;
        return Objects.equals(id, comment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

