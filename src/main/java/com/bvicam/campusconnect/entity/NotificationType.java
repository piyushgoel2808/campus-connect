package com.bvicam.campusconnect.entity;

/**
 * Enumeration of supported in-app and email notification categories in Campus Connect.
 *
 * <p>Used by {@link Notification} and {@code NotificationService} to categorize alerts
 * and check the corresponding user preference flags on {@link User}
 * ({@code notifyMessages}, {@code notifyEvents}, {@code notifyJobs}).</p>
 */
public enum NotificationType {
    /** Triggered when a user receives a new 1-on-1 direct chat message. */
    MESSAGE,

    /** Triggered when a new campus event is scheduled for the user's department (or campus-wide). */
    EVENT,

    /** Triggered when a new job or internship opportunity is posted for the user's department (or campus-wide). */
    JOB
}

