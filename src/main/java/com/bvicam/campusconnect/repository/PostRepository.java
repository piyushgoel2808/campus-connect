package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for managing community feed {@link Post} entities.
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findAllByOrderByIsPinnedDescCreatedAtDesc();

    Page<Post> findAllByOrderByIsPinnedDescCreatedAtDesc(Pageable pageable);

    List<Post> findByAuthorId(Long authorId);
}
