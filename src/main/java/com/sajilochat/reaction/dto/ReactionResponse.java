
package com.sajilochat.reaction.dto;

import java.time.Instant;

public record ReactionResponse(
        Long id,
        Long messageId,
        Long conversationId,
        Long userId,
        String username,
        String emoji,
        Instant createdAt,
        Instant updatedAt
) {
}
