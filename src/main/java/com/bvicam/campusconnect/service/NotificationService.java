package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.NotificationDto;
import com.bvicam.campusconnect.entity.Notification;
import com.bvicam.campusconnect.entity.NotificationType;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.NotificationRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service responsible for creating, filtering, persisting, and dispatching real-time in-app notifications.
 */
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
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return null;
        }
        if (!shouldDeliver(recipientEmail, type)) {
            return null;
        }

        Notification notification = new Notification();
        notification.setRecipientEmail(recipientEmail);
        notification.setType(type);
        notification.setTitle(truncate(title, 120));
        notification.setBody(truncate(body, 500));
        notification.setRelatedEntityId(relatedEntityId);
        notification.setRead(false);

        Notification saved = notificationRepository.save(notification);
        NotificationDto dto = NotificationDto.from(saved);

        messagingTemplate.convertAndSendToUser(recipientEmail, "/queue/notifications", dto);
        return dto;
    }

    @Async
    public void notifyAllUsersExceptAsync(String excludedEmail,
                                          NotificationType type,
                                          String title,
                                          String body,
                                          Long relatedEntityId) {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (excludedEmail != null && excludedEmail.equalsIgnoreCase(user.getEmail())) {
                continue;
            }
            if (isEnabledForType(user, type)) {
                dispatchForUser(user.getEmail(), type, title, body, relatedEntityId);
            }
        }
    }

    @Async
    public void notifyRoleAsync(Role role,
                                NotificationType type,
                                String title,
                                String body,
                                Long relatedEntityId) {
        List<User> targets = userRepository.findByRole(role);
        for (User target : targets) {
            if (isEnabledForType(target, type)) {
                dispatchForUser(target.getEmail(), type, title, body, relatedEntityId);
            }
        }
    }

    private void dispatchForUser(String recipientEmail,
                                 NotificationType type,
                                 String title,
                                 String body,
                                 Long relatedEntityId) {
        Notification notification = new Notification();
        notification.setRecipientEmail(recipientEmail);
        notification.setType(type);
        notification.setTitle(truncate(title, 120));
        notification.setBody(truncate(body, 500));
        notification.setRelatedEntityId(relatedEntityId);
        notification.setRead(false);

        Notification saved = notificationRepository.save(notification);
        messagingTemplate.convertAndSendToUser(recipientEmail, "/queue/notifications", NotificationDto.from(saved));
    }

    private boolean shouldDeliver(String recipientEmail, NotificationType type) {
        return userRepository.findByEmail(recipientEmail)
                .map(user -> isEnabledForType(user, type))
                .orElse(false);
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

    private String truncate(String val, int max) {
        if (val == null) return "";
        return val.length() <= max ? val : val.substring(0, max);
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

    @Transactional
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

    @Transactional
    public int markAllRead(String recipientEmail) {
        return notificationRepository.markAllReadForRecipient(recipientEmail);
    }
}
