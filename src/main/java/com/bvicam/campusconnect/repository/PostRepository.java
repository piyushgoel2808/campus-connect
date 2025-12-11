package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByOrderByCreatedAtDesc(); // Newest posts first
}