package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for managing user-submitted {@link Feedback} entities.
 */
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findAllByOrderBySubmittedAtDesc();

    void deleteBySubmittedById(Long userId);
}
