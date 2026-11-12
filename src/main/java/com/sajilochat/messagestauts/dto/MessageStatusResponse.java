
package com.sajilochat.messagestatus.dto;

import com.sajilochat.messagestatus.enums.DeliveryStatus;

import java.time.Instant;

public record MessageStatusResponse(
        Long id,
        Long messageId,
        Long recipientId,
        String recipientUsername,
        DeliveryStatus status,
        Instant deliveredAt,
        Instant readAt
) {
}
