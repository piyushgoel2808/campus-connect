package com.bvicam.campusconnect.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class EventDTO {
    private Long id;
    private String title;
    private String description;
    private String location;

    // ✅ Ensures date is sent as a String, not an array
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dateTime;

    // ✅ CHANGED: Wrapper classes (Boolean, Integer) handle nulls safely
    private Boolean attending;
    private Integer participantCount;

    // 1. Default Constructor
    public EventDTO() {
    }

    // 2. All-Arguments Constructor
    public EventDTO(Long id, String title, String description, String location, LocalDateTime dateTime, Boolean attending, Integer participantCount) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.location = location;
        this.dateTime = dateTime;
        this.attending = attending;
        this.participantCount = participantCount;
    }

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