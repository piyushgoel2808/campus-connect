package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.NotificationDto;
import com.bvicam.campusconnect.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for In-App Notifications ({@code /api/notifications}).
 * <p>
 * Provides endpoints for fetching recent notifications for the logged-in user,
 * polling or initializing the unread notification badge counter, and marking
 * individual or all notifications as read.
 * </p>
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /**
     * Retrieves recent notifications (top 10 or top 20) for the authenticated user.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/notifications?limit={10|20}}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Query Parameter:</b> {@code limit} (optional) - max items to fetch</li>
     *   <li><b>Response:</b> {@code 200 OK} with JSON array of {@link NotificationDto}</li>
     * </ul>
     *
     * @param limit     optional cap on the number of returned notifications
     * @param principal the authenticated user's principal
     * @return a {@link ResponseEntity} containing recent notifications
     */
    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(@RequestParam(required = false) Integer limit,
                                                                  Principal principal) {
        return ResponseEntity.ok(notificationService.getRecent(principal.getName(), limit));
    }

    /**
     * Returns the count of unread notifications for the authenticated user.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/notifications/unread-count}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Response:</b> {@code 200 OK} with {@code {"unread": <count>}}</li>
     * </ul>
     *
     * @param principal the authenticated user's principal
     * @return a {@link ResponseEntity} with a map containing the {@code unread} count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Principal principal) {
        long unread = notificationService.getUnreadCount(principal.getName());
        return ResponseEntity.ok(Map.of("unread", unread));
    }

    /**
     * Marks a specific notification as read if it belongs to the authenticated user.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code PUT /api/notifications/{id}/read}</li>
     *   <li><b>Access Role:</b> Notification Recipient (Authenticated User)</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the notification</li>
     *   <li><b>Response:</b> {@code 200 OK} on success, or {@code 404 Not Found} if missing/not owned</li>
     * </ul>
     *
     * @param id        the ID of the notification to mark read
     * @param principal the authenticated user's principal
     * @return a {@link ResponseEntity} confirming read status update or 404 error
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, Principal principal) {
        boolean updated = notificationService.markRead(principal.getName(), id);
        if (!updated) {
            return ResponseEntity.status(404).body(Map.of("error", "Notification not found"));
        }
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    /**
     * Marks all recent unread notifications for the authenticated user as read.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code PUT /api/notifications/read-all}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Response:</b> {@code 200 OK} with {@code {"updated": <count>}}</li>
     * </ul>
     *
     * @param principal the authenticated user's principal
     * @return a {@link ResponseEntity} containing the number of updated notifications
     */
    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(Principal principal) {
        int updated = notificationService.markAllRead(principal.getName());
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
