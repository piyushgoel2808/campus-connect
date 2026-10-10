package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.NotificationDto;
import com.bvicam.campusconnect.entity.Notification;
import com.bvicam.campusconnect.entity.NotificationType;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.NotificationRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for creating, filtering, persisting, and dispatching real-time
 * in-app notifications.
 * <p>
 * Respects per-user notification preferences ({@code notifyMessages}, {@code notifyEvents},
 * {@code notifyJobs}) before saving a {@link Notification} to MySQL and pushing it over
 * WebSocket STOMP to {@code /user/{recipientEmail}/queue/notifications}.
 * </p>
 */
@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // =========================================================
    // 1. NOTIFICATION CREATION & REAL-TIME DISPATCH
    // =========================================================

    /**
     * Creates a new notification for a user, saves it to the database, and pushes it
     * in real time via WebSocket if the user has enabled notifications for the given type.
     *
     * @param recipientEmail  the email address of the target user
     * @param type            the category of notification ({@code MESSAGE}, {@code EVENT}, or {@code JOB})
     * @param title           short summary heading for the notification
     * @param body            descriptive body text for the notification
     * @param relatedEntityId optional ID of the associated entity (message ID, event ID, or job ID)
     * @return the created {@link NotificationDto}, or {@code null} if delivery was suppressed by user preferences
     */
    public NotificationDto createAndDispatch(String recipientEmail,
                                             NotificationType type,
                                             String title,
                                             String body,
                                             Long relatedEntityId) {
        // Step 1: Check whether the recipient has opted out of this notification category
        if (!shouldDeliver(recipientEmail, type)) {
            return null;
        }

        // Step 2: Construct and populate the Notification entity (initially unread)
        Notification notification = new Notification();
        notification.setRecipientEmail(recipientEmail);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRelatedEntityId(relatedEntityId);
        notification.setRead(false);

        // Step 3: Persist to database and convert to a lightweight DTO
        Notification saved = notificationRepository.save(notification);
        NotificationDto dto = NotificationDto.from(saved);

        // Step 4: Push real-time notification over STOMP WebSocket to the recipient's private queue
        messagingTemplate.convertAndSendToUser(recipientEmail, "/queue/notifications", dto);
        return dto;
    }

    // =========================================================
    // 2. USER PREFERENCE FILTERING HELPERS
    // =========================================================

    /**
     * Checks whether a notification of the given {@link NotificationType} should be delivered
     * to the user identified by {@code recipientEmail}. Defaults to {@code true} if the user
     * record is not found.
     *
     * @param recipientEmail the recipient's email address
     * @param type           the notification category to check
     * @return {@code true} if delivery is allowed, {@code false} if muted by the user
     */
    private boolean shouldDeliver(String recipientEmail, NotificationType type) {
        return userRepository.findByEmail(recipientEmail)
                .map(user -> isEnabledForType(user, type))
                .orElse(true);
    }

    /**
     * Evaluates a user's preference flags for a specific {@link NotificationType}.
     * Null preference fields are treated as enabled ({@code true}) by default.
     *
     * @param user the {@link User} entity whose preferences are inspected
     * @param type the notification category
     * @return {@code true} if enabled (or unset), {@code false} if explicitly disabled
     */
    private boolean isEnabledForType(User user, NotificationType type) {
        if (type == NotificationType.MESSAGE) {
            return user.getNotifyMessages() == null || user.getNotifyMessages();
        }
        if (type == NotificationType.EVENT) {
            return user.getNotifyEvents() == null || user.getNotifyEvents();
        }
        if (type == NotificationType.JOB) {
            return user.getNotifyJobs() == null || user.getNotifyJobs();
        }
        return true;
    }

    // =========================================================
    // 3. NOTIFICATION QUERYING & READ-STATE MANAGEMENT
    // =========================================================

    /**
     * Fetches the most recent notifications for a user (either top 10 or top 20 depending on {@code limit}).
     *
     * @param recipientEmail the email address of the user
     * @param limit          optional maximum number of items to return (uses top 10 if {@code <= 10}, else top 20)
     * @return a list of recent {@link NotificationDto} objects ordered newest-first
     */
    public List<NotificationDto> getRecent(String recipientEmail, Integer limit) {
        List<Notification> items = (limit != null && limit <= 10)
                ? notificationRepository.findTop10ByRecipientEmailOrderByCreatedAtDesc(recipientEmail)
                : notificationRepository.findTop20ByRecipientEmailOrderByCreatedAtDesc(recipientEmail);

        return items.stream().map(NotificationDto::from).toList();
    }

    /**
     * Returns the total count of unread notifications for a user.
     *
     * @param recipientEmail the email address of the user
     * @return number of unread notifications
     */
    public long getUnreadCount(String recipientEmail) {
        return notificationRepository.countByRecipientEmailAndIsReadFalse(recipientEmail);
    }

    /**
     * Marks a single notification as read, verifying that it belongs to the requesting user.
     *
     * @param recipientEmail the email address of the authenticated user making the request
     * @param id             the ID of the notification to mark as read
     * @return {@code true} if found, owned by the user, and marked read; {@code false} otherwise
     */
    public boolean markRead(String recipientEmail, Long id) {
        return notificationRepository.findById(id).map(notification -> {
            // Security check: ensure users can only mark their own notifications as read
            if (!recipientEmail.equalsIgnoreCase(notification.getRecipientEmail())) {
                return false;
            }
            notification.setRead(true);
            notificationRepository.save(notification);
            return true;
        }).orElse(false);
    }

    /**
     * Marks up to the 20 most recent unread notifications for a user as read in bulk.
     *
     * @param recipientEmail the email address of the authenticated user
     * @return the number of notifications that transitioned from unread to read
     */
    public int markAllRead(String recipientEmail) {
        List<Notification> notifications = notificationRepository.findTop20ByRecipientEmailOrderByCreatedAtDesc(recipientEmail);
        int updated = 0;

        for (Notification notification : notifications) {
            if (!notification.isRead()) {
                notification.setRead(true);
                updated++;
            }
        }

        if (updated > 0) {
            notificationRepository.saveAll(notifications);
        }
        return updated;
    }
}
