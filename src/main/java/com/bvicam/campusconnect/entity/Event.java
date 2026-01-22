package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Data
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private LocalDateTime dateTime;
    private String location;
    private String organizerName; // e.g. "Alumni Association"

    // ✅ NEW: List of Students/Alumni who are going
    @ManyToMany
    @JoinTable(
            name = "event_participants",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @JsonIgnore // Hide full list from default JSON to protect data
    private Set<User> participants = new HashSet<>();

    // ✅ NEW: Helper field for Frontend (Not stored in DB)
    @Transient
    private boolean isAttending;

    @Transient
    private int participantCount;
}