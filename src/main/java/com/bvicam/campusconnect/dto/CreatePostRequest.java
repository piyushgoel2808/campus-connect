package com.bvicam.campusconnect.dto;

import lombok.Data;

/**
 * Request DTO for creating a Campus Wall post (prevents JPA entity mass assignment).
 */
@Data
public class CreatePostRequest {
    private String content;
    private String imageUrl;
}
