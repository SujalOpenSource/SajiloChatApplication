
package com.sajilochat.notification.websocket;

import com.sajilochat.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(
            String recipientUsername,
            NotificationResponse notification
    ) {

        log.info(
                "Publishing notification {} to user {}",
                notification.id(),
                recipientUsername
        );

        messagingTemplate.convertAndSendToUser(
                recipientUsername,
                "/queue/notifications",
                notification
        );
    }
}
