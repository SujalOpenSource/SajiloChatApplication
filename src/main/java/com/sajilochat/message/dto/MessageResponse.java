
package com.sajilochat.message.dto;

import com.sajilochat.message.enums.MessageType;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long conversationId,
        SenderResponse sender,
        MessageType type,
        String content,
        boolean edited,
        Instant createdAt,
        Instant updatedAt
) {
    public record SenderResponse(
            Long id,
            String username,
            String firstName,
            String lastName
    ) {}
}
