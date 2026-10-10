package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.entity.Job;
import com.bvicam.campusconnect.entity.NotificationType;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.JobRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.service.NotificationService;
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

    @Autowired
    private NotificationService notificationService;

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
        Job savedJob = jobRepository.save(job);

        List<User> users = userRepository.findAll();
        for (User target : users) {
            if (target.getRole() == Role.STUDENT) {
                notificationService.createAndDispatch(
                        target.getEmail(),
                        NotificationType.JOB,
                        "New job opportunity",
                        savedJob.getTitle() + " at " + savedJob.getCompany(),
                        savedJob.getId()
                );
            }
        }

        return ResponseEntity.ok("Job posted successfully!");
    }

    // 3. Edit / Update a Job or Referral (Admin or Job Poster)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateJob(@PathVariable Long id, @RequestBody Job updatedJob) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Job job = jobRepository.findById(id).orElse(null);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = job.getPostedBy() != null && job.getPostedBy().getId().equals(user.getId());

        if (!isAdmin && !isOwner) {
            return ResponseEntity.status(403).body("Not authorized to edit this job.");
        }

        if (updatedJob.getTitle() != null && !updatedJob.getTitle().trim().isEmpty()) {
            job.setTitle(updatedJob.getTitle().trim());
        }
        if (updatedJob.getCompany() != null && !updatedJob.getCompany().trim().isEmpty()) {
            job.setCompany(updatedJob.getCompany().trim());
        }
        if (updatedJob.getLocation() != null) {
            job.setLocation(updatedJob.getLocation().trim());
        }
        if (updatedJob.getDescription() != null) {
            job.setDescription(updatedJob.getDescription().trim());
        }
        if (updatedJob.getApplyLink() != null) {
            job.setApplyLink(updatedJob.getApplyLink().trim());
        }

        jobRepository.save(job);
        return ResponseEntity.ok("Job updated successfully!");
    }

    // 4. Delete a Job or Referral (Admin or Job Poster)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJob(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        Job job = jobRepository.findById(id).orElse(null);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = job.getPostedBy() != null && job.getPostedBy().getId().equals(user.getId());

        if (isAdmin || isOwner) {
            jobRepository.deleteById(id);
            return ResponseEntity.ok("Job deleted.");
        }
        return ResponseEntity.status(403).body("Not authorized.");
    }
}