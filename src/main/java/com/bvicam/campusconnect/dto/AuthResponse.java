package com.bvicam.campusconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Data Transfer Object (DTO) returned by {@code POST /api/auth/login} upon successful authentication.
 */
@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long id;
    private String name;
    private String role;
    private boolean passwordChanged;

    public AuthResponse(String token, Long id, String name, String role) {
        this(token, id, name, role, true);
    }
}
