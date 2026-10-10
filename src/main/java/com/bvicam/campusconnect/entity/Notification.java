package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity representing an in-app notification delivered to a specific user.
 *
 * <p><b>Database Table &amp; Architectural Role:</b></p>
 * <ul>
 *   <li>Maps to the {@code notifications} table in MySQL.</li>
 *   <li>Created by {@code NotificationService} whenever a user receives a private message
 *       ({@link NotificationType#MESSAGE}), a new campus event is published ({@link NotificationType#EVENT}),
 *       or a new job is posted ({@link NotificationType#JOB}), respecting the user's notification preferences.</li>
 *   <li>Persisted in the database for history/unread counts and simultaneously pushed in real time
 *       over STOMP WebSockets to {@code /topic/notifications/{recipientEmail}} via
 *       {@link com.bvicam.campusconnect.dto.NotificationDto}.</li>
 * </ul>
 */
@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    /** Auto-incremented primary key uniquely identifying this notification. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email address of the user who should receive this notification. */
    @Column(nullable = false)
    private String recipientEmail;

    /** Category of notification (e.g., {@code MESSAGE}, {@code EVENT}, {@code JOB}), stored as a string in DB. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    /** Short summary heading displayed in the notification bell dropdown (max 120 chars). */
    @Column(nullable = false, length = 120)
    private String title;

    /** Detailed notification message text (max 500 chars). */
    @Column(nullable = false, length = 500)
    private String body;

    /** Optional primary key ID of the associated entity (e.g., Event ID, Job ID, or PrivateMessage ID) for deep-linking. */
    private Long relatedEntityId;

    /** Tracks whether the recipient has viewed/dismissed this notification (defaults to {@code false}). */
    @Column(nullable = false)
    private boolean isRead = false;

    /** Timestamp when the notification was generated. */
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

