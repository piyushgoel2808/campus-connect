package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.CreateJobRequest;
import com.bvicam.campusconnect.entity.Job;
import com.bvicam.campusconnect.entity.NotificationType;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.JobRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST Controller for Job & Referral Opportunities ({@code /api/jobs}).
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public List<Job> getAllJobs(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            int safePage = Math.max(0, page != null ? page : 0);
            int safeSize = Math.min(100, Math.max(1, size != null ? size : 20));
            return jobRepository.findAllByOrderByPostedAtDesc(PageRequest.of(safePage, safeSize)).getContent();
        }
        return jobRepository.findAllByOrderByPostedAtDesc();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ALUMNI', 'ADMIN')")
    public ResponseEntity<?> postJob(@RequestBody CreateJobRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().trim().isEmpty()
                || request.getCompany() == null || request.getCompany().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Job title and company are required."));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        if (user.getRole() != Role.ALUMNI && user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Only Alumni or Admins can post jobs!"));
        }

        Job job = new Job();
        job.setTitle(request.getTitle().trim());
        job.setCompany(request.getCompany().trim());
        job.setLocation(request.getLocation() != null ? request.getLocation().trim() : null);
        job.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        job.setApplyLink(request.getApplyLink() != null ? request.getApplyLink().trim() : null);
        job.setPostedBy(user);

        Job savedJob = jobRepository.save(job);

        notificationService.notifyRoleAsync(
                Role.STUDENT,
                NotificationType.JOB,
                "New job opportunity",
                savedJob.getTitle() + " at " + savedJob.getCompany(),
                savedJob.getId()
        );

        return ResponseEntity.ok(Map.of("message", "Job posted successfully!", "id", savedJob.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateJob(@PathVariable Long id, @RequestBody CreateJobRequest updatedJob) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        Job job = jobRepository.findById(id).orElse(null);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = job.getPostedBy() != null && job.getPostedBy().getId().equals(user.getId());

        if (!isAdmin && !isOwner) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized to edit this job."));
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
        return ResponseEntity.ok(Map.of("message", "Job updated successfully!"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJob(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        Job job = jobRepository.findById(id).orElse(null);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = job.getPostedBy() != null && job.getPostedBy().getId().equals(user.getId());

        if (isAdmin || isOwner) {
            jobRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Job deleted."));
        }
        return ResponseEntity.status(403).body(Map.of("error", "Not authorized."));
    }
}
