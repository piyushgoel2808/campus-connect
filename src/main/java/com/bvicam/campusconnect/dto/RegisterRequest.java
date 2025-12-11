package com.bvicam.campusconnect.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String email;
    private String password; // Plain text password from user
    private String name;
    private String role;     // STUDENT, ALUMNI
    private String enrollmentNumber;
}