package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.NotificationDto;
import com.bvicam.campusconnect.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(@RequestParam(required = false) Integer limit,
                                                                  Principal principal) {
        return ResponseEntity.ok(notificationService.getRecent(principal.getName(), limit));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Principal principal) {
        long unread = notificationService.getUnreadCount(principal.getName());
        return ResponseEntity.ok(Map.of("unread", unread));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, Principal principal) {
        boolean updated = notificationService.markRead(principal.getName(), id);
        if (!updated) {
            return ResponseEntity.status(404).body(Map.of("error", "Notification not found"));
        }
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(Principal principal) {
        int updated = notificationService.markAllRead(principal.getName());
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
