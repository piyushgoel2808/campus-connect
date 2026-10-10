package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Spring Data JPA repository for managing {@link User} entities.
 * <p>
 * Supports authentication lookups by email, duplicate email checks during registration,
 * and dynamic multi-criteria filtering for the student/alumni directory.
 * </p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user account by its unique email address.
     * Used during login authentication, JWT validation, and profile lookups.
     *
     * @param email the user's email address
     * @return an {@link java.util.Optional} containing the {@link User} if found, or empty otherwise
     */
    // Find by Email (Existing)
    java.util.Optional<User> findByEmail(String email);

    /**
     * Checks whether a user account with the given email address already exists in the database.
     *
     * @param email the email address to check
     * @return {@code true} if an account with this email exists, {@code false} otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Performs a case-insensitive directory search across multiple user profile fields with optional filters.
     * <ul>
     *   <li>If {@code role} is {@code null}, users of all roles are included.</li>
     *   <li>If {@code batch} is {@code null}, users from all graduation batches are included.</li>
     *   <li>If {@code keyword} is {@code null}, no text filter is applied; otherwise matches against
     *       name, email, skills, currentCompany, headline, or designation.</li>
     * </ul>
     * Results are sorted alphabetically by the user's name.
     *
     * @param role    optional {@link Role} filter (e.g., {@code STUDENT}, {@code ALUMNI})
     * @param batch   optional graduation batch year filter (e.g., {@code 2025})
     * @param keyword optional free-text search term
     * @return a list of matching {@link User} entities ordered by name ascending
     */
    // ✅ Advanced Search Query
    // Searches Name, Email, Skills, Company, Headline, or Designation. Filters by Role and Batch if provided.
    @Query("SELECT u FROM User u WHERE " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:batch IS NULL OR u.batchYear = :batch) AND " +
            "(:keyword IS NULL OR lower(u.name) LIKE lower(concat('%', :keyword, '%')) " +
            "OR lower(u.email) LIKE lower(concat('%', :keyword, '%')) " +
            "OR lower(u.skills) LIKE lower(concat('%', :keyword, '%')) " +
            "OR lower(u.currentCompany) LIKE lower(concat('%', :keyword, '%')) " +
            "OR lower(u.headline) LIKE lower(concat('%', :keyword, '%')) " +
            "OR lower(u.designation) LIKE lower(concat('%', :keyword, '%'))) " +
            "ORDER BY u.name ASC")
    List<User> searchUsers(@Param("role") Role role,
                           @Param("batch") Integer batch,
                           @Param("keyword") String keyword);
}