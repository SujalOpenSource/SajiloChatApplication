
package com.sajilochat.messagestatus.websocket;

import com.sajilochat.messagestatus.dto.MessageStatusEvent;
import com.sajilochat.messagestatus.entity.MessageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageStatusWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishStatusUpdate(MessageStatus status) {

        MessageStatusEvent event = new MessageStatusEvent(
                status.getMessage().getId(),
                status.getMessage().getConversation().getId(),
                status.getRecipient().getId(),
                status.getRecipient().getUsername(),
                status.getStatus(),
                status.getDeliveredAt(),
                status.getReadAt()
        );

        String senderUsername =
                status.getMessage().getSender().getUsername();

        messagingTemplate.convertAndSendToUser(
                senderUsername,
                "/queue/message-status",
                event
        );
    }
}
