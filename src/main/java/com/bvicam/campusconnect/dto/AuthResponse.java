package com.bvicam.campusconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Data Transfer Object (DTO) returned by {@code POST /api/auth/login} upon successful authentication.
 *
 * <p><b>API Contract &amp; Frontend Usage:</b></p>
 * <ul>
 *   <li>Provides the signed JWT bearer {@link #token} that the frontend stores in {@code localStorage}
 *       and attaches to subsequent requests in the {@code Authorization: Bearer <token>} header.</li>
 *   <li>Includes lightweight user identity metadata ({@link #id}, {@link #name}, {@link #role}) so the
 *       dashboard UI can immediately render role-specific navigation without an extra round-trip.</li>
 * </ul>
 */
@Data
@AllArgsConstructor
public class AuthResponse {
    /** Signed HS256 JWT bearer token valid for 10 hours. */
    private String token;

    /** Database primary key of the authenticated user. */
    private Long id;

    /** Full display name of the authenticated user. */
    private String name;

    /** Role name of the authenticated user ({@code "STUDENT"}, {@code "ALUMNI"}, or {@code "ADMIN"}). */
    private String role;
}