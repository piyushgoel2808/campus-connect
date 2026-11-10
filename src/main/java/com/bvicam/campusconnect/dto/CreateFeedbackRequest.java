package com.bvicam.campusconnect.dto;

import lombok.Data;

/**
 * Request DTO for submitting platform feedback with a 1–5 rating.
 */
@Data
public class CreateFeedbackRequest {
    private Integer rating;
    private String comments;
}
