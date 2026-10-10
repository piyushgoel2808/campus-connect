package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Spring Data JPA repository for managing {@link Department} entities.
 * <p>
 * Used during user registration, admin user creation, and data seeding to look up
 * academic departments by their short code (e.g., {@code "MCA"}, {@code "BJMC"}).
 * </p>
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /**
     * Finds a department by its unique short code (e.g., "MCA", "BJMC").
     *
     * @param code the unique department code to search for
     * @return an {@link Optional} containing the matching {@link Department} if found, or empty otherwise
     */
    Optional<Department> findByCode(String code);
}