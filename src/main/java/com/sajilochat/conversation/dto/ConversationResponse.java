
package com.sajilochat.conversation.dto;

import com.sajilochat.conversation.enums.ConversationType;

import java.time.Instant;
import java.util.List;

public record ConversationResponse(
        Long id,
        ConversationType type,
        String name,
        Long createdById,
        List<MemberResponse> members,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt
) {
    public record MemberResponse(
            Long userId,
            String username,
            String firstName,
            String lastName,
            Instant joinedAt
    ) {}
}
