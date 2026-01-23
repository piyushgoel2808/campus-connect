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

@Entity
@Table(name = "event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 1000) // Allows longer descriptions
    private String description;

    // JSON Format matches the HTML datetime-local input
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dateTime;

    private String location;

    // Kept optional organizer name
    private String organizerName;

    @ManyToMany
    @JoinTable(
            name = "event_participants",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    // Initialize to empty set to avoid NullPointerExceptions
    private Set<User> participants = new HashSet<>();

    // --- NOTE: Frontend helpers (isAttending, participantCount) are now handled by EventDTO ---

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