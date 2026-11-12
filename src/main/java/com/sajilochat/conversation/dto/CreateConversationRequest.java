
package com.sajilochat.conversation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateConversationRequest(

        @NotNull(message = "Target user ID is required")
        @Positive(message = "Target user ID must be positive")
        Long targetUserId

) {}
