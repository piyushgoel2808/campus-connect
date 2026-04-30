package com.bvicam.campusconnect.dto;

import com.bvicam.campusconnect.entity.Notification;
import com.bvicam.campusconnect.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private NotificationType type;
    private String title;
    private String body;
    private Long relatedEntityId;
    private boolean read;
    private LocalDateTime createdAt;

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
