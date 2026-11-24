
package com.sajilochat.group.event;

import com.sajilochat.message.dto.MessageResponse;

public record GroupSystemMessageEvent(
        Long conversationId,
        MessageResponse message
) {
}
