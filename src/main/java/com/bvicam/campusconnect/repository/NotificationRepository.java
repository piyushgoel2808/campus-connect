package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop20ByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    List<Notification> findTop10ByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    long countByRecipientEmailAndIsReadFalse(String recipientEmail);
}
