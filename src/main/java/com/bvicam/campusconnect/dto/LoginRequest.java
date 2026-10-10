package com.bvicam.campusconnect.dto;

import lombok.Data;

/**
 * Data Transfer Object (DTO) encapsulating user credentials sent to {@code POST /api/auth/login}.
 *
 * <p>Deserialized from the JSON request body by {@code AuthController}, which verifies the
 * {@link #email} and plaintext {@link #password} against the BCrypt hash stored in {@link com.bvicam.campusconnect.entity.User}.</p>
 */
@Data
public class LoginRequest {
    /** Registered institutional or personal email address of the user attempting to sign in. */
    private String email;

    /** Plaintext password submitted from the login form (compared against the stored BCrypt hash). */
    private String password;
}