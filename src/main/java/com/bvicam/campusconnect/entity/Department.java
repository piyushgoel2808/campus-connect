package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA Entity representing an academic department or course program at BVICAM (e.g., MCA, BJMC).
 *
 * <p><b>Database Table &amp; Architectural Usage:</b></p>
 * <ul>
 *   <li>Maps to the {@code departments} table in MySQL.</li>
 *   <li>Seeded at startup by {@link com.bvicam.campusconnect.config.DataSeeder} and managed by admins.</li>
 *   <li>Referenced by {@link User#getDepartment()} to associate students/alumni with their academic program,
 *       and optionally by {@link Event#getTargetDepartment()} and {@link Job#getTargetDepartment()} to
 *       target events or job postings to a specific department.</li>
 * </ul>
 */
@Entity
@Table(name = "departments")
@Data
@NoArgsConstructor
public class Department {

    /** Auto-incremented primary key uniquely identifying this department. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Full human-readable name of the department (must be unique and non-null). */
    @Column(unique = true, nullable = false)
    private String name; // e.g., "Master of Computer Applications"

    /** Short uppercase department code used in dropdowns and filters (must be unique and non-null). */
    @Column(unique = true, nullable = false)
    private String code; // e.g., "MCA", "BJMC"

    /**
     * Convenience constructor for creating a new department with a full name and short code.
     *
     * @param name full department title (e.g., "Master of Computer Applications")
     * @param code short department code (e.g., "MCA")
     */
    public Department(String name, String code) {
        this.name = name;
        this.code = code;
    }
}