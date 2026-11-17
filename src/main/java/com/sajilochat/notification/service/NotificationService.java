
package com.sajilochat.notification.service;

import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.message.entity.Message;
import com.sajilochat.notification.dto.NotificationResponse;
import com.sajilochat.notification.entity.Notification;
import com.sajilochat.notification.enums.NotificationType;
import com.sajilochat.notification.event.NotificationCreatedEvent;
import com.sajilochat.notification.repository.NotificationRepository;
import com.sajilochat.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates notifications for all conversation members
     * except the sender.
     */
    @Transactional
    public void createNewMessageNotifications(Message message) {

        var conversation = message.getConversation();
        User sender = message.getSender();

        log.info("Creating notifications for message {}", message.getId());

        for (var member : conversation.getMembers()) {

            User recipient = member.getUser();

            if (recipient.getId().equals(sender.getId())) {
                continue;
            }

            Notification notification = Notification.create(
                    recipient,
                    sender,
                    NotificationType.NEW_MESSAGE,
                    conversation,
                    message,
                    sender.getUsername() + " sent you a message"
            );

            saveAndPublish(notification, recipient);
        }
    }

    /**
     * Creates and publishes a notification for a group event.
     *
     * The calling service decides which group members
     * should receive the notification.
     */
    @Transactional
    public NotificationResponse createGroupNotification(
            User recipient,
            User actor,
            NotificationType type,
            Conversation conversation,
            String content
    ) {

        Notification notification = Notification.create(
                recipient,
                actor,
                type,
                conversation,
                null,
                content
        );

        return saveAndPublish(notification, recipient);
    }

    /**
     * Saves a notification and publishes its creation event.
     */
    private NotificationResponse saveAndPublish(
            Notification notification,
            User recipient
    ) {

        Notification savedNotification =
                notificationRepository.save(notification);

        NotificationResponse response =
                mapToResponse(savedNotification);

        eventPublisher.publishEvent(
                new NotificationCreatedEvent(
                        recipient.getUsername(),
                        response
                )
        );

        log.info(
                "Notification {} created for {}",
                savedNotification.getId(),
                recipient.getUsername()
        );

        return response;
    }

    /**
     * Retrieves all notifications belonging to a user.
     */
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

    /**
     * Retrieves unread notifications.
     */
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

    /**
     * Counts unread notifications.
     */
    public long getUnreadCount(Long userId) {

        return notificationRepository
                .countByRecipientIdAndReadAtIsNull(userId);
    }

    /**
     * Marks one notification as read.
     */
    @Transactional
    public NotificationResponse markAsRead(
            Long notificationId,
            Long userId
    ) {

        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Notification not found"
                        )
                );

        notification.markAsRead();

        return mapToResponse(notification);
    }

    /**
     * Marks all unread notifications as read.
     */
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
