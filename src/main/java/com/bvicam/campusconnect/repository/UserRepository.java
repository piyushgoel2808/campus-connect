package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    // Find by Email (Existing)
    java.util.Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // ✅ NEW: Advanced Search Query
    // Searches Name OR Skills OR Company. Filters by Role and Batch if provided.
    @Query("SELECT u FROM User u WHERE " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:batch IS NULL OR u.batchYear = :batch) AND " +
            "(:keyword IS NULL OR lower(u.name) LIKE lower(concat('%', :keyword, '%')) " +
            "OR lower(u.skills) LIKE lower(concat('%', :keyword, '%')) " +
            "OR lower(u.currentCompany) LIKE lower(concat('%', :keyword, '%'))) " +
            "ORDER BY u.name ASC")
    List<User> searchUsers(@Param("role") String role,
                           @Param("batch") Integer batch,
                           @Param("keyword") String keyword);
}