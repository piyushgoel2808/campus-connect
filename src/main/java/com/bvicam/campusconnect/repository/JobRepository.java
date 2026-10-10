package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for managing {@link Job} and referral opportunity entities.
 * <p>
 * Used by {@code JobController} and {@code AdminController} to create, update, delete,
 * and list alumni-posted job opportunities.
 * </p>
 */
public interface JobRepository extends JpaRepository<Job, Long> {

    /**
     * Retrieves all job postings ordered by their creation timestamp descending
     * (newest job opportunities first).
     *
     * @return a list of {@link Job} entities sorted newest-first
     */
    // Fetch jobs sorted by newest first
    List<Job> findAllByOrderByPostedAtDesc();
}