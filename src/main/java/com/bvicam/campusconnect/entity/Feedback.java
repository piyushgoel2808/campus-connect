package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * JPA Entity representing platform feedback submitted by a student or alumnus.
 *
 * <p><b>Database Table &amp; Relationships:</b></p>
 * <ul>
 *   <li>Maps to the {@code feedback} table in MySQL.</li>
 *   <li>{@code submittedBy} ({@link ManyToOne}): Links each feedback submission to the
 *       authenticated {@link User} via the {@code user_id} foreign key column.</li>
 *   <li>Managed via {@code FeedbackController} (users submit feedback; admins review all submissions).</li>
 * </ul>
 */
@Entity
@Data
@Table(name = "feedback")
public class Feedback {

    /** Auto-incremented primary key uniquely identifying this feedback entry. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Star rating score selected by the user (1 = lowest to 5 = highest). */
    private int rating; // 1 to 5

    /** Free-text suggestions, bug reports, or comments (up to 1,000 characters). */
    @Column(length = 1000)
    private String comments;

    /** The authenticated user who submitted this feedback. */
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User submittedBy;

    /** Timestamp when the feedback was recorded (defaults to current server time). */
    private LocalDateTime submittedAt = LocalDateTime.now();
}