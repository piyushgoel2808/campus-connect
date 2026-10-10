package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for managing user-submitted {@link Feedback} entities.
 * <p>
 * Enables students and alumni to persist campus feedback and allows administrators
 * to review submissions in reverse chronological order.
 * </p>
 */
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /**
     * Retrieves all feedback entries sorted by submission timestamp descending
     * so administrators see the most recent feedback first.
     *
     * @return a list of all {@link Feedback} records ordered newest-first
     */
    // Admin needs to see newest feedback first
    List<Feedback> findAllByOrderBySubmittedAtDesc();
}