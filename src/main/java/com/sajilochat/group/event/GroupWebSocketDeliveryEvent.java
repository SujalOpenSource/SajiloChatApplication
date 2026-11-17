
package com.sajilochat.group.event;

import com.sajilochat.group.dto.GroupEvent;

public record GroupWebSocketDeliveryEvent(
        String recipientUsername,
        GroupEvent groupEvent
) {
}
