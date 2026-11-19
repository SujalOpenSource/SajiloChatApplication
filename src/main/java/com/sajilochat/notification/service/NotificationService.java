
package com.sajilochat.notification.service;

import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.conversation.enums.ConversationType;
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

import java.time.Instant;
import com.sajilochat.notification.enums.NotificationType;
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int MAX_NOTIFICATION_CONTENT_LENGTH = 500;

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void createNewMessageNotifications(Message message) {

        Conversation conversation = message.getConversation();
        User sender = message.getSender();

        String preview = createMessagePreview(message);

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
                    preview
            );

            saveAndPublish(notification, recipient);
        }
    }

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

        log.debug(
                "Notification {} created for user {}",
                savedNotification.getId(),
                recipient.getId()
        );

        return response;
    }

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
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Notification not found"
                        )
                );

        notification.markAsRead();

        return mapToResponse(notification);
    }

    @Transactional
    public int markAllAsRead(Long userId) {

        return notificationRepository.markAllUnreadAsRead(
                userId,
                Instant.now()
        );
    }

    private Pageable createPageable(int page, int size) {

        if (page < 0) {
            throw new BadRequestException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new BadRequestException(
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
        Conversation conversation = notification.getConversation();

        Long conversationId = conversation != null
                ? conversation.getId()
                : null;

        ConversationType conversationType = conversation != null
                ? conversation.getType()
                : null;

        String conversationName = conversation != null
                ? conversation.getName()
                : null;

        long groupUnreadCount = 0;

        if (conversation != null
                && conversation.getType() == ConversationType.GROUP) {

            groupUnreadCount =
                    notificationRepository
                            .countByRecipientIdAndConversationIdAndTypeAndReadAtIsNull(
                                    notification.getRecipient().getId(),
                                    conversation.getId(),
                                    NotificationType.NEW_MESSAGE
                            );
        }

        return new NotificationResponse(
                notification.getId(),
                notification.getRecipient().getId(),
                actor != null ? actor.getId() : null,
                actor != null ? actor.getUsername() : null,
                notification.getType(),
                conversationId,
                conversationType,
                conversationName,
                notification.getMessage() != null
                        ? notification.getMessage().getId()
                        : null,
                notification.getContent(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.isRead(),
                groupUnreadCount
        );
    }

    private String createMessagePreview(Message message) {

        String content = message.getContent();

        if (content == null || content.isBlank()) {
            return "Sent a message";
        }

        String normalizedContent = content.trim();

        if (normalizedContent.length()
                <= MAX_NOTIFICATION_CONTENT_LENGTH) {
            return normalizedContent;
        }

        return normalizedContent.substring(
                0,
                MAX_NOTIFICATION_CONTENT_LENGTH - 3
        ) + "...";
    }
}
