
package com.sajilochat.notification.event;

import com.sajilochat.notification.dto.NotificationResponse;

public record NotificationCreatedEvent(
        String recipientUsername,
        NotificationResponse notification
) {
}
