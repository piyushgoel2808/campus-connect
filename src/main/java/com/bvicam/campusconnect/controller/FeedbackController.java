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

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    // 1. Submit Feedback (Student/Alumni)
    @PostMapping
    public ResponseEntity<?> submitFeedback(@RequestBody Feedback feedback) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        feedback.setSubmittedBy(user);
        feedbackRepository.save(feedback);
        return ResponseEntity.ok("Feedback submitted successfully!");
    }

    // 2. View All Feedback (Admin Only)
    @GetMapping
    public List<Feedback> getAllFeedback() {
        return feedbackRepository.findAllByOrderBySubmittedAtDesc();
    }
}