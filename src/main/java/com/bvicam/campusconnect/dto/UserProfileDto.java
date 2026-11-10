package com.bvicam.campusconnect.dto;

import com.bvicam.campusconnect.entity.User;
import lombok.Data;

/**
 * Client-safe Data Transfer Object representing a {@link User} profile.
 */
@Data
public class UserProfileDto {

    private Long id;
    private String name;
    private String email;
    private String role;
    private String departmentName;
    private String departmentCode;
    private Integer batchYear;
    private String enrollmentNumber;
    private String headline;
    private String currentCompany;
    private String designation;
    private String skills;
    private String pastExperience;
    private String linkedinUrl;
    private String githubUrl;
    private Boolean notifyMessages;
    private Boolean notifyEvents;
    private Boolean notifyJobs;
    private Boolean isFlagged;
    private Integer flagCount;
    private Boolean passwordChanged;

    public static UserProfileDto fromEntity(User user) {
        if (user == null) {
            return null;
        }
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole() != null ? user.getRole().name() : null);
        dto.setDepartmentName(user.getDepartment() != null ? user.getDepartment().getName() : null);
        dto.setDepartmentCode(user.getDepartment() != null ? user.getDepartment().getCode() : null);
        dto.setBatchYear(user.getBatchYear());
        dto.setEnrollmentNumber(user.getEnrollmentNumber());
        dto.setHeadline(user.getHeadline());
        dto.setCurrentCompany(user.getCurrentCompany());
        dto.setDesignation(user.getDesignation());
        dto.setSkills(user.getSkills());
        dto.setPastExperience(user.getPastExperience());
        dto.setLinkedinUrl(user.getLinkedinUrl());
        dto.setGithubUrl(user.getGithubUrl());
        dto.setNotifyMessages(user.getNotifyMessages());
        dto.setNotifyEvents(user.getNotifyEvents());
        dto.setNotifyJobs(user.getNotifyJobs());
        dto.setIsFlagged(user.getIsFlagged() != null ? user.getIsFlagged() : false);
        dto.setFlagCount(user.getFlagCount() != null ? user.getFlagCount() : 0);
        dto.setPasswordChanged(user.getPasswordChanged() == null || user.getPasswordChanged());
        return dto;
    }
}
