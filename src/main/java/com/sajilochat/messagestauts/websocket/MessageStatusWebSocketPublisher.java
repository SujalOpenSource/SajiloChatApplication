
package com.sajilochat.messagestatus.websocket;

import com.sajilochat.messagestatus.dto.MessageStatusEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageStatusWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishStatusUpdate(
            String senderUsername,
            MessageStatusEvent event
    ) {
        messagingTemplate.convertAndSendToUser(
                senderUsername,
                "/queue/message-status",
                event
        );
    }
}
