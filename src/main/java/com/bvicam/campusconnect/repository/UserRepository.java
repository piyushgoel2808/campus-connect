package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for managing {@link User} entities.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByBatchYear(Integer batchYear);

    List<User> findByRole(Role role);

    List<User> findByEmailIn(Collection<String> emails);

    @Query("SELECT u.email FROM User u")
    List<String> findAllEmails();

    @Query("SELECT u FROM User u WHERE " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:batch IS NULL OR u.batchYear = :batch) AND " +
            "(:keyword IS NULL OR (" +
            "  lower(u.name) LIKE lower(concat('%', :keyword, '%')) OR " +
            "  lower(u.email) LIKE lower(concat('%', :keyword, '%')) OR " +
            "  lower(u.skills) LIKE lower(concat('%', :keyword, '%')) OR " +
            "  lower(u.currentCompany) LIKE lower(concat('%', :keyword, '%')) OR " +
            "  lower(u.headline) LIKE lower(concat('%', :keyword, '%')) OR " +
            "  lower(u.designation) LIKE lower(concat('%', :keyword, '%'))" +
            ")) " +
            "ORDER BY u.name ASC")
    List<User> searchUsers(@Param("role") Role role,
                           @Param("batch") Integer batch,
                           @Param("keyword") String keyword);
}
