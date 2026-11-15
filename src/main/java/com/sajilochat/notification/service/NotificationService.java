
package com.sajilochat.notification.service;

import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.notification.dto.NotificationResponse;
import com.sajilochat.notification.entity.Notification;
import com.sajilochat.notification.repository.NotificationRepository;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public Page<NotificationResponse> getMyNotifications(
            Long userId,
            int page,
            int size
    ) {
        Pageable pageable = createPageable(page, size);

        return notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDesc(
                        userId,
                        pageable
                )
                .map(this::mapToResponse);
    }

    public Page<NotificationResponse> getMyUnreadNotifications(
            Long userId,
            int page,
            int size
    ) {
        Pageable pageable = createPageable(page, size);

        return notificationRepository
                .findAllByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(
                        userId,
                        pageable
                )
                .map(this::mapToResponse);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository
                .countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(
            Long notificationId,
            Long userId
    ) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found"
                ));

        notification.markAsRead();

        return mapToResponse(notification);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        var notifications = notificationRepository
                .findAllByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(
                        userId,
                        Pageable.unpaged()
                );

        notifications.forEach(Notification::markAsRead);

        return notifications.getNumberOfElements();
    }

    private Pageable createPageable(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100"
            );
        }

        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    private NotificationResponse mapToResponse(
            Notification notification
    ) {
        User actor = notification.getActor();

        return new NotificationResponse(
                notification.getId(),
                notification.getRecipient().getId(),
                actor != null ? actor.getId() : null,
                actor != null ? actor.getUsername() : null,
                notification.getType(),
                notification.getConversation() != null
                        ? notification.getConversation().getId()
                        : null,
                notification.getMessage() != null
                        ? notification.getMessage().getId()
                        : null,
                notification.getContent(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.isRead()
        );
    }
}
