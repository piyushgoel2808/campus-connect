package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Spring Data JPA repository for managing community feed {@link Post} entities.
 * <p>
 * Provides standard CRUD operations and a custom feed query that prioritizes
 * administrator-pinned announcements at the top of the feed.
 * </p>
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * Retrieves all posts ordered first by pinned status ({@code isPinned = true} before {@code false})
     * and then chronologically by creation timestamp (newest first).
     *
     * @return a list of all {@link Post} entities sorted for the main campus feed
     */
    // Sort by: Pinned (True first) -> Then Date (Newest first)
    List<Post> findAllByOrderByIsPinnedDescCreatedAtDesc();
}