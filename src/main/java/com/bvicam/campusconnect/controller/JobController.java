package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.entity.Job;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.JobRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserRepository userRepository;

    // 1. Get All Jobs (Everyone)
    @GetMapping
    public List<Job> getAllJobs() {
        return jobRepository.findAllByOrderByPostedAtDesc();
    }

    // 2. Post a Job (Alumni Only)
    @PostMapping
    public ResponseEntity<?> postJob(@RequestBody Job job) {
        // Get currently logged-in user's email from Security Context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email).orElseThrow();

        // Security Check: Is this user an ALUMNI or ADMIN?
        if (!"ALUMNI".equalsIgnoreCase(user.getRole().name()) && !"ADMIN".equalsIgnoreCase(user.getRole().name())) {
            return ResponseEntity.status(403).body("Only Alumni can post jobs!");
        }

        job.setPostedBy(user);
        jobRepository.save(job);
        return ResponseEntity.ok("Job posted successfully!");
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJob(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        Job job = jobRepository.findById(id).orElseThrow();

        // Allow if ADMIN or if the user Posted it
        if (user.getRole().name().equals("ADMIN") || job.getPostedBy().getId().equals(user.getId())) {
            jobRepository.deleteById(id);
            return ResponseEntity.ok("Job deleted.");
        }
        return ResponseEntity.status(403).body("Not authorized.");
    }
}