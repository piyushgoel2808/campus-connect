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

    // ✅ ADD THIS MISSING FIELD
    private Integer batchYear;
    private String departmentCode;
}