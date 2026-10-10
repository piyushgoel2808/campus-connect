package com.bvicam.campusconnect.dto;

import com.bvicam.campusconnect.entity.Notification;
import com.bvicam.campusconnect.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) representing an in-app notification delivered to the frontend via
 * REST ({@code /api/notifications}) or real-time STOMP WebSocket ({@code /topic/notifications/{email}}).
 *
 * <p>Exposes a clean client-facing contract (omitting internal fields like {@code recipientEmail})
 * and provides a static factory method {@link #from(Notification)} for one-line entity conversion.</p>
 */
@Data
@AllArgsConstructor
public class NotificationDto {
    /** Unique database ID of the notification. */
    private Long id;

    /** Category of the notification ({@code MESSAGE}, {@code EVENT}, or {@code JOB}). */
    private NotificationType type;

    /** Short headline displayed in the notification bell list. */
    private String title;

    /** Detailed descriptive body of the notification. */
    private String body;

    /** Optional ID of the target entity (Event, Job, or Message) for UI navigation. */
    private Long relatedEntityId;

    /** Whether the user has already marked this notification as read. */
    private boolean read;

    /** Timestamp when the notification was created. */
    private LocalDateTime createdAt;

    /**
     * Converts a persisted {@link Notification} JPA entity into a client-safe {@link NotificationDto}.
     *
     * @param notification the source JPA entity
     * @return a populated {@link NotificationDto} instance
     */
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getRelatedEntityId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}

