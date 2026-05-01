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

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public NotificationDto createAndDispatch(String recipientEmail,
                                             NotificationType type,
                                             String title,
                                             String body,
                                             Long relatedEntityId) {
        if (!shouldDeliver(recipientEmail, type)) {
            return null;
        }

        Notification notification = new Notification();
        notification.setRecipientEmail(recipientEmail);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRelatedEntityId(relatedEntityId);
        notification.setRead(false);

        Notification saved = notificationRepository.save(notification);
        NotificationDto dto = NotificationDto.from(saved);

        messagingTemplate.convertAndSendToUser(recipientEmail, "/queue/notifications", dto);
        return dto;
    }

    private boolean shouldDeliver(String recipientEmail, NotificationType type) {
        return userRepository.findByEmail(recipientEmail)
                .map(user -> isEnabledForType(user, type))
                .orElse(true);
    }

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

    public List<NotificationDto> getRecent(String recipientEmail, Integer limit) {
        List<Notification> items = (limit != null && limit <= 10)
                ? notificationRepository.findTop10ByRecipientEmailOrderByCreatedAtDesc(recipientEmail)
                : notificationRepository.findTop20ByRecipientEmailOrderByCreatedAtDesc(recipientEmail);

        return items.stream().map(NotificationDto::from).toList();
    }

    public long getUnreadCount(String recipientEmail) {
        return notificationRepository.countByRecipientEmailAndIsReadFalse(recipientEmail);
    }

    public boolean markRead(String recipientEmail, Long id) {
        return notificationRepository.findById(id).map(notification -> {
            if (!recipientEmail.equalsIgnoreCase(notification.getRecipientEmail())) {
                return false;
            }
            notification.setRead(true);
            notificationRepository.save(notification);
            return true;
        }).orElse(false);
    }

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
