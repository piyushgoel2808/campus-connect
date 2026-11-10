package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for managing in-app {@link Notification} entities.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop20ByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    List<Notification> findTop10ByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    long countByRecipientEmailAndIsReadFalse(String recipientEmail);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientEmail = :email AND n.isRead = false")
    int markAllReadForRecipient(@Param("email") String email);

    void deleteByRecipientEmail(String recipientEmail);
}
