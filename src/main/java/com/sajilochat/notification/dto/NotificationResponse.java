
package com.sajilochat.notification.dto;

import com.sajilochat.notification.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(

        Long id,

        Long recipientId,

        Long actorId,

        String actorUsername,

        NotificationType type,

        Long conversationId,

        Long messageId,

        String content,

        Instant createdAt,

        Instant readAt,

        boolean read

) {
}
