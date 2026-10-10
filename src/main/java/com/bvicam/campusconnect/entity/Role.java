package com.bvicam.campusconnect.entity;

/**
 * Enumeration of user authorization roles within the Campus Connect platform.
 *
 * <p>Stored as a {@code VARCHAR} string in the {@code users.role} column via
 * {@code @Enumerated(EnumType.STRING)} and mapped to Spring Security authorities
 * by {@code CustomUserDetailsService}.</p>
 */
public enum Role {
    /** Currently enrolled student; can post on the campus wall, RSVP to events, browse jobs, and chat with alumni/peers. */
    STUDENT,

    /** Graduated alumnus; has all student capabilities plus permission to post job/internship referrals on the Job Board. */
    ALUMNI,

    /** Platform administrator; can bulk-upload users, manage departments, create events, moderate content, and review flagged accounts/feedback. */
    ADMIN
}