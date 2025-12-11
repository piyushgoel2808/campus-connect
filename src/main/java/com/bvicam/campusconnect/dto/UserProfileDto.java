package com.bvicam.campusconnect.dto;

import lombok.Data;

@Data
public class UserProfileDto {
    private String headline;
    private String currentCompany;
    private String designation;
    private String skills;
    private String pastExperience;
    private String linkedinUrl;
    private String githubUrl;
}