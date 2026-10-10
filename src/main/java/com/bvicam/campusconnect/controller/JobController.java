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

/**
 * REST Controller for Job & Referral Opportunities ({@code /api/jobs}).
 * <p>
 * Enables all authenticated users to browse job openings and referrals, while restricting
 * job creation to {@link Role#ALUMNI} and {@link Role#ADMIN} users, and restricting edits
 * and deletions to the original job poster or an {@link Role#ADMIN}.
 * </p>
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

    // =========================================================
    // 1. GET ALL JOBS
    // =========================================================

    /**
     * Retrieves all job and referral postings ordered by posting date descending (newest first).
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/jobs}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Response:</b> {@code 200 OK} with JSON array of {@link Job} entities</li>
     * </ul>
     *
     * @return a list of all {@link Job} postings sorted newest-first
     */
    // 1. Get All Jobs (Everyone)
    @GetMapping
    public List<Job> getAllJobs() {
        return jobRepository.findAllByOrderByPostedAtDesc();
    }

    // =========================================================
    // 2. POST A NEW JOB OR REFERRAL
    // =========================================================

    /**
     * Publishes a new job or referral opportunity and notifies all registered {@link Role#STUDENT} users.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/jobs}</li>
     *   <li><b>Access Role:</b> {@code ALUMNI} or {@code ADMIN} only (returns {@code 403 Forbidden} for Students)</li>
     *   <li><b>Request Payload:</b> {@link Job} JSON object ({@code title}, {@code company}, {@code location}, {@code description}, {@code applyLink})</li>
     *   <li><b>Response:</b> {@code 200 OK} on success, or {@code 403 Forbidden} if unauthorized</li>
     * </ul>
     *
     * @param job the {@link Job} entity to create
     * @return a {@link ResponseEntity} confirming job creation or denying access
     */
    // 2. Post a Job (Alumni Only)
    @PostMapping
    public ResponseEntity<?> postJob(@RequestBody Job job) {
        // Step 1: Get currently logged-in user's email from Security Context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email).orElseThrow();

        // Step 2: Security Check - Is this user an ALUMNI or ADMIN?
        if (!"ALUMNI".equalsIgnoreCase(user.getRole().name()) && !"ADMIN".equalsIgnoreCase(user.getRole().name())) {
            return ResponseEntity.status(403).body("Only Alumni can post jobs!");
        }

        // Step 3: Link the poster and save the job to MySQL
        job.setPostedBy(user);
        Job savedJob = jobRepository.save(job);

        // Step 4: Dispatch real-time in-app notifications to all STUDENT accounts
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

    // =========================================================
    // 3. UPDATE A JOB OR REFERRAL
    // =========================================================

    /**
     * Updates an existing job posting. Allowed only for the original job poster or an {@code ADMIN}.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code PUT /api/jobs/{id}}</li>
     *   <li><b>Access Role:</b> Job Owner ({@code ALUMNI}) or {@code ADMIN}</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the job to update</li>
     *   <li><b>Request Payload:</b> {@link Job} JSON object containing updated fields</li>
     *   <li><b>Response:</b> {@code 200 OK} on success, {@code 403 Forbidden} if unauthorized, or {@code 404 Not Found}</li>
     * </ul>
     *
     * @param id         the ID of the job to edit
     * @param updatedJob the updated job details
     * @return a {@link ResponseEntity} indicating the outcome of the update
     */
    // 3. Edit / Update a Job or Referral (Admin or Job Poster)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateJob(@PathVariable Long id, @RequestBody Job updatedJob) {
        // Step 1: Resolve the authenticated user and target job
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Job job = jobRepository.findById(id).orElse(null);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        // Step 2: Verify that the requester is either an ADMIN or the original poster
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = job.getPostedBy() != null && job.getPostedBy().getId().equals(user.getId());

        if (!isAdmin && !isOwner) {
            return ResponseEntity.status(403).body("Not authorized to edit this job.");
        }

        // Step 3: Apply non-null field updates
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

        // Step 4: Save updated job entity
        jobRepository.save(job);
        return ResponseEntity.ok("Job updated successfully!");
    }

    // =========================================================
    // 4. DELETE A JOB OR REFERRAL
    // =========================================================

    /**
     * Deletes a job posting by ID. Allowed only for the original job poster or an {@code ADMIN}.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code DELETE /api/jobs/{id}}</li>
     *   <li><b>Access Role:</b> Job Owner ({@code ALUMNI}) or {@code ADMIN}</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the job to delete</li>
     *   <li><b>Response:</b> {@code 200 OK} on deletion, {@code 403 Forbidden} if unauthorized, or {@code 404 Not Found}</li>
     * </ul>
     *
     * @param id the ID of the job to delete
     * @return a {@link ResponseEntity} confirming deletion or error status
     */
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