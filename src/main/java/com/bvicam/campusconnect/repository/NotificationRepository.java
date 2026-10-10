package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for managing in-app {@link Notification} entities.
 * <p>
 * Supports fetching recent notifications for a specific user (capped at 10 or 20 items)
 * and counting unread notifications for the header badge counter.
 * </p>
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Fetches up to the 20 most recent notifications for a recipient, ordered newest-first.
     *
     * @param recipientEmail the email address of the user receiving the notifications
     * @return a list of up to 20 {@link Notification} entities sorted by {@code createdAt} descending
     */
    List<Notification> findTop20ByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    /**
     * Fetches up to the 10 most recent notifications for a recipient, ordered newest-first.
     *
     * @param recipientEmail the email address of the user receiving the notifications
     * @return a list of up to 10 {@link Notification} entities sorted by {@code createdAt} descending
     */
    List<Notification> findTop10ByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    /**
     * Counts the number of unread notifications ({@code isRead == false}) for the given user.
     *
     * @param recipientEmail the email address of the recipient user
     * @return the count of unread notifications
     */
    long countByRecipientEmailAndIsReadFalse(String recipientEmail);
}
