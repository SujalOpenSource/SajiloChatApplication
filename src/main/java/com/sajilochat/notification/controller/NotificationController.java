
package com.sajilochat.notification.controller;

import com.sajilochat.notification.dto.NotificationResponse;
import com.sajilochat.notification.service.NotificationService;
import com.sajilochat.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Get all notifications.
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {

        return ResponseEntity.ok(
                notificationService.getMyNotifications(
                        principal.getUserId(),
                        page,
                        size
                )
        );
    }

    /**
     * Get unread notifications.
     */
    @GetMapping("/unread")
    public ResponseEntity<Page<NotificationResponse>> getMyUnreadNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {

        return ResponseEntity.ok(
                notificationService.getMyUnreadNotifications(
                        principal.getUserId(),
                        page,
                        size
                )
        );
    }

    /**
     * Get unread notification count.
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {

        long count = notificationService.getUnreadCount(
                principal.getUserId()
        );

        return ResponseEntity.ok(
                Map.of("unreadCount", count)
        );
    }

    /**
     * Mark one notification as read.
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {

        return ResponseEntity.ok(
                notificationService.markAsRead(
                        notificationId,
                        principal.getUserId()
                )
        );
    }

    /**
     * Mark all notifications as read.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal
    ) {

        int updatedCount = notificationService.markAllAsRead(
                principal.getUserId()
        );

        return ResponseEntity.ok(
                Map.of("updatedCount", updatedCount)
        );
    }
}
