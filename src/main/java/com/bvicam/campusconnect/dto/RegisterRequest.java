package com.bvicam.campusconnect.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private String role; // STUDENT or ALUMNI
    private String enrollmentNumber;

    // ✅ ADD THIS MISSING FIELD
    private Integer batchYear;
}