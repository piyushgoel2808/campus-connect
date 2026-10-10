package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for managing {@link Comment} entities.
 * <p>
 * Provides CRUD operations and custom derived query methods for fetching and counting
 * comments and nested replies associated with community feed posts.
 * </p>
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Retrieves all comments (both top-level comments and nested replies) for a given post,
     * ordered chronologically from oldest to newest.
     *
     * @param postId the ID of the post whose comments should be fetched
     * @return a chronological list of all {@link Comment} records linked to the post
     */
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    /**
     * Retrieves only top-level comments (where {@code parentComment} is {@code null}) for a given post,
     * ordered chronologically from oldest to newest.
     * <p>
     * Note: Each top-level {@link Comment} eagerly loads its nested {@code replies} collection,
     * preventing duplicate rendering of child replies at the root level.
     * </p>
     *
     * @param postId the ID of the post whose top-level comments should be fetched
     * @return a chronological list of top-level {@link Comment} objects for the post
     */
    List<Comment> findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(Long postId);

    /**
     * Counts the total number of comments (including nested replies) associated with a specific post.
     *
     * @param postId the ID of the post
     * @return total comment count for the post
     */
    long countByPostId(Long postId);
}
