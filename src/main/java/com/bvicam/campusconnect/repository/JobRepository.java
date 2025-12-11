package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    // Fetch jobs sorted by newest first
    List<Job> findAllByOrderByPostedAtDesc();
}