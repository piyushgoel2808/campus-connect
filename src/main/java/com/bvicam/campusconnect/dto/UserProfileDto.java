package com.bvicam.campusconnect.dto;

import lombok.Data;

@Data
public class UserProfileDto {
    private Long id;
    private String name;
    private String email;
    private String role;
    private String departmentName;

    private String headline;
    private String currentCompany;
    private String designation;
    private String skills;
    private String pastExperience;
    private String linkedinUrl;
    private String githubUrl;
}