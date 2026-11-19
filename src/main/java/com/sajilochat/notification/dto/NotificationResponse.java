
package com.sajilochat.notification.dto;

import com.sajilochat.conversation.enums.ConversationType;
import com.sajilochat.notification.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(

        Long id,

        Long recipientId,

        Long actorId,

        String actorUsername,

        NotificationType type,

        Long conversationId,

        ConversationType conversationType,

        String conversationName,

        Long messageId,

        String content,

        Instant createdAt,

        Instant readAt,

        boolean read,

        long groupUnreadCount

) {
}
