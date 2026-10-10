package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.entity.Feedback;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.FeedbackRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Campus Feedback Submission and Review ({@code /api/feedback}).
 * <p>
 * Allows authenticated students and alumni to submit platform or campus feedback,
 * and allows administrators to review all submitted feedback in reverse chronological order.
 * </p>
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    // =========================================================
    // 1. SUBMIT FEEDBACK
    // =========================================================

    /**
     * Submits a new feedback entry linked to the currently authenticated user.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/feedback}</li>
     *   <li><b>Access Role:</b> Authenticated User ({@code STUDENT}, {@code ALUMNI})</li>
     *   <li><b>Request Payload:</b> {@link Feedback} JSON object ({@code subject}, {@code message}, {@code rating})</li>
     *   <li><b>Response:</b> {@code 200 OK} with confirmation message</li>
     * </ul>
     *
     * @param feedback the {@link Feedback} payload submitted by the user
     * @return a {@link ResponseEntity} confirming submission
     */
    // 1. Submit Feedback (Student/Alumni)
    @PostMapping
    public ResponseEntity<?> submitFeedback(@RequestBody Feedback feedback) {
        // Step 1: Identify the currently logged-in user from Spring Security context
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        // Step 2: Associate the author and persist the feedback record
        feedback.setSubmittedBy(user);
        feedbackRepository.save(feedback);
        return ResponseEntity.ok("Feedback submitted successfully!");
    }

    // =========================================================
    // 2. VIEW ALL FEEDBACK
    // =========================================================

    /**
     * Retrieves all user-submitted feedback ordered newest-first.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/feedback}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Response:</b> {@code 200 OK} with JSON array of {@link Feedback} entities</li>
     * </ul>
     *
     * @return a list of all {@link Feedback} entries ordered by submission date descending
     */
    // 2. View All Feedback (Admin Only)
    @GetMapping
    public List<Feedback> getAllFeedback() {
        return feedbackRepository.findAllByOrderBySubmittedAtDesc();
    }
}