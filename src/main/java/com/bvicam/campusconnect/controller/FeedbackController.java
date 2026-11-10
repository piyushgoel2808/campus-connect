package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.CreateFeedbackRequest;
import com.bvicam.campusconnect.entity.Feedback;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.FeedbackRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST Controller for Campus Feedback Submission and Review ({@code /api/feedback}).
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> submitFeedback(@RequestBody CreateFeedbackRequest request) {
        if (request == null || request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rating must be between 1 and 5."));
        }

        String comments = request.getComments() != null ? request.getComments().trim() : "";
        if (comments.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comments must be 1000 characters or fewer."));
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Feedback feedback = new Feedback();
        feedback.setRating(request.getRating());
        feedback.setComments(comments);
        feedback.setSubmittedBy(user);

        feedbackRepository.save(feedback);
        return ResponseEntity.ok(Map.of("message", "Feedback submitted successfully!"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Feedback> getAllFeedback() {
        return feedbackRepository.findAllByOrderBySubmittedAtDesc();
    }
}
