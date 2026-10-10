package com.bvicam.campusconnect.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) for transferring campus {@link com.bvicam.campusconnect.entity.Event}
 * details to the frontend without exposing raw JPA entity collections.
 *
 * <p><b>Why This DTO Exists:</b></p>
 * <ul>
 *   <li>Decouples the REST response of {@code /api/events} from the bidirectional
 *       {@code Event.participants <-> User.events} JPA relationship, preventing lazy-loading errors
 *       and unnecessary exposure of every attendee's full {@code User} object.</li>
 *   <li>Pre-computes user-specific RSVP state ({@link #attending}) and total headcount
 *       ({@link #participantCount}) on the server side in {@code EventController}.</li>
 * </ul>
 */
public class EventDTO {

    // =========================================================================
    // Fields
    // =========================================================================

    /** Unique database identifier of the event. */
    private Long id;

    /** Title or headline of the event. */
    private String title;

    /** Full description or agenda of the event. */
    private String description;

    /** Physical venue or online meeting location. */
    private String location;

    // ✅ Ensures date is sent as an ISO-like String ("yyyy-MM-dd'T'HH:mm"), not a Jackson numeric array
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dateTime;

    // ✅ CHANGED: Wrapper classes (Boolean, Integer) handle nulls safely during serialization/deserialization
    /** Whether the currently authenticated user has RSVP'd to attend this event. */
    private Boolean attending;

    /** Total number of users currently registered to attend this event. */
    private Integer participantCount;

    // =========================================================================
    // Constructors
    // =========================================================================

    // 1. Default Constructor (Required by Jackson for JSON deserialization)
    public EventDTO() {
    }

    // 2. All-Arguments Constructor (Used by EventController when mapping Event entities to DTOs)
    public EventDTO(Long id, String title, String description, String location, LocalDateTime dateTime, Boolean attending, Integer participantCount) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.location = location;
        this.dateTime = dateTime;
        this.attending = attending;
        this.participantCount = participantCount;
    }

    // =========================================================================
    // Getters and Setters
    // =========================================================================

    // 3. Getters and Setters

    public Integer getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(Integer participantCount) {
        this.participantCount = participantCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    // Changed getter name from isAttending() to getAttending() for Boolean wrapper convention
    public Boolean getAttending() {
        return attending;
    }

    public void setAttending(Boolean attending) {
        this.attending = attending;
    }
}