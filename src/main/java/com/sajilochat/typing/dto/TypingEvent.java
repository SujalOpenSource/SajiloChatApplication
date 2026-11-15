
package com.sajilochat.typing.dto;

import jakarta.validation.constraints.NotNull;

public record TypingEvent(
        @NotNull Long conversationId,
        @NotNull Boolean typing
) {
}
