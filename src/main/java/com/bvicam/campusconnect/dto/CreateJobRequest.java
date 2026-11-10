package com.bvicam.campusconnect.dto;

import lombok.Data;

/**
 * Request DTO for creating or updating a {@link com.bvicam.campusconnect.entity.Job} listing.
 */
@Data
public class CreateJobRequest {
    private String title;
    private String company;
    private String location;
    private String description;
    private String applyLink;
}
