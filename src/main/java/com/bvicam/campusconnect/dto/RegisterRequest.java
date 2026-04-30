package com.bvicam.campusconnect.dto;

import com.bvicam.campusconnect.entity.Role;
import lombok.Data;

@Data
public class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private Role role; // STUDENT or ALUMNI
    private String enrollmentNumber;
    private Integer batchYear;
    private String departmentCode;
    
    // Professional Profile Fields
    private String headline;
    private String currentCompany;
    private String designation;
    private String skills;
    private String githubUrl;
    private String linkedinUrl;
    private String pastExperience;
}