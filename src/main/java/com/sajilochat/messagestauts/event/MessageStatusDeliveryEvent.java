
package com.sajilochat.messagestatus.event;

import com.sajilochat.messagestatus.dto.MessageStatusEvent;

public record MessageStatusDeliveryEvent(
        String senderUsername,
        MessageStatusEvent statusEvent
) {
}
