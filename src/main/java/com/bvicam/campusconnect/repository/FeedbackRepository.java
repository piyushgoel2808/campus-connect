package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    // Admin needs to see newest feedback first
    List<Feedback> findAllByOrderBySubmittedAtDesc();
}