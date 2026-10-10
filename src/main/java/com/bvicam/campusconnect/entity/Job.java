package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * JPA Entity representing a job or internship opportunity posted on the Campus Connect Job Board.
 *
 * <p><b>Database Table &amp; Relationships:</b></p>
 * <ul>
 *   <li>Maps to the {@code jobs} table in MySQL.</li>
 *   <li>{@code postedBy} ({@link ManyToOne}): References the {@link User} (typically an {@code ALUMNI}
 *       or {@code ADMIN}) who shared the opening via the {@code posted_by_id} foreign key.</li>
 *   <li>{@code targetDepartment} ({@link ManyToOne}): Optional {@link Department} filter; if {@code null},
 *       the job opportunity is applicable to students/alumni across all departments.</li>
 * </ul>
 */
@Entity
@Table(name = "jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    /** Auto-incremented primary key uniquely identifying this job listing. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Role title or position name (e.g., "Junior Java Developer"). */
    private String title;

    /** Name of the hiring organization or company. */
    private String company;

    /** Work location (e.g., "Noida", "Remote", "Bangalore"). */
    private String location;

    /** Role responsibilities, eligibility criteria, and required skills (up to 1,000 characters). */
    @Column(length = 1000) // Allow longer descriptions
    private String description;

    /** Application URL or recruiter email address where candidates can apply. */
    private String applyLink; // Email or URL

    /** The Alumnus or Admin user who posted this job opportunity. */
    @ManyToOne
    @JoinColumn(name = "posted_by_id")
    private User postedBy; // Links the job to the Alumnus who posted it

    /** Timestamp when the job was published (defaults to current time upon instantiation). */
    private LocalDateTime postedAt = LocalDateTime.now();

    /** Optional department the job is tailored for (null means open to all departments). */
    @ManyToOne
    @JoinColumn(name = "target_department_id")
    private Department targetDepartment;
}