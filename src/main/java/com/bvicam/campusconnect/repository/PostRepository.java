package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // Sort by: Pinned (True first) -> Then Date (Newest first)
    List<Post> findAllByOrderByIsPinnedDescCreatedAtDesc();
}