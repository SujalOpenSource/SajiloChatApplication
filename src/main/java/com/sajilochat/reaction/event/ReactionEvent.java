
package com.sajilochat.reaction.event;

import com.sajilochat.reaction.dto.ReactionResponse;

public record ReactionEvent(
        Long conversationId,
        String action,
        ReactionResponse reaction
) {
}
