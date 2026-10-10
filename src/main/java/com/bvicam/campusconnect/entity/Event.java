package com.bvicam.campusconnect.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * JPA Entity representing a campus event, workshop, seminar, or alumni meet.
 *
 * <p><b>Database Table &amp; Relationships:</b></p>
 * <ul>
 *   <li>Maps to the {@code event} table in MySQL.</li>
 *   <li>{@code targetDepartment} ({@link ManyToOne}): Optional {@link Department} restriction;
 *       if {@code null}, the event is open/visible to all campus departments.</li>
 *   <li>{@code participants} ({@link ManyToMany}): Tracks RSVP'd attendees via the
 *       {@code event_participants} join table ({@code event_id} &lt;-&gt; {@code user_id}).</li>
 *   <li>Uses {@link Getter}/{@link Setter} instead of {@code @Data} along with ID-based
 *       {@link #equals(Object)} and {@link #hashCode()} to prevent {@link StackOverflowError}
 *       when Hibernate evaluates bidirectional {@code Set<User>} / {@code Set<Event>} collections.</li>
 * </ul>
 */
@Entity
@Table(name = "event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    // =========================================================================
    // Core Event Attributes
    // =========================================================================

    /** Auto-incremented primary key uniquely identifying this event. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Title or headline of the campus event. */
    private String title;

    /** Detailed description or agenda of the event (up to 1,000 characters). */
    @Column(length = 1000) // Allows longer descriptions
    private String description;

    // JSON Format matches the HTML datetime-local input (e.g., "2026-05-15T14:30")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dateTime;

    /** Physical venue, room number, or online meeting link where the event takes place. */
    private String location;

    // Kept optional organizer name (name of the admin/faculty/alumnus hosting the event)
    private String organizerName;

    // =========================================================================
    // Relationships (Department Targeting & RSVP Participants)
    // =========================================================================

    /** Optional target department; null indicates a campus-wide event for all departments. */
    @ManyToOne
    @JoinColumn(name = "target_department_id")
    private Department targetDepartment;

    /**
     * Set of users who have registered/RSVP'd to attend this event.
     * Mapped via the {@code event_participants} join table.
     */
    @ManyToMany
    @JoinTable(
            name = "event_participants",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    // Initialize to empty set to avoid NullPointerExceptions when adding/counting attendees
    private Set<User> participants = new HashSet<>();

    // --- NOTE: Frontend helpers (isAttending, participantCount) are now handled by EventDTO ---

    // =========================================================================
    // Entity Identity (ID-Only equals & hashCode)
    // =========================================================================

    // --- CRITICAL FIX: Custom equals and hashCode (ONLY ID) ---
    // This prevents StackOverflowError/Infinite Loops when calculating lists
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(id, event.id); // Only check ID!
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // Only hash ID!
    }
}