package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for managing {@link Job} and referral opportunity entities.
 */
public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findAllByOrderByPostedAtDesc();

    Page<Job> findAllByOrderByPostedAtDesc(Pageable pageable);

    void deleteByPostedById(Long userId);
}
