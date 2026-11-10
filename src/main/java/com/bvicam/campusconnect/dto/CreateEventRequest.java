package com.bvicam.campusconnect.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Request DTO for creating or updating a campus {@link com.bvicam.campusconnect.entity.Event}.
 */
@Data
public class CreateEventRequest {
    private String title;
    private String description;
    private String location;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dateTime;

    private String organizerName;
}
