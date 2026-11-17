
package com.sajilochat.group.websocket;

import com.sajilochat.group.dto.GroupEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(
            String recipientUsername,
            GroupEvent event
    ) {

        log.info(
                "Publishing group event {} for group {} to user {}",
                event.eventType(),
                event.groupId(),
                recipientUsername
        );

        messagingTemplate.convertAndSendToUser(
                recipientUsername,
                "/queue/group-events",
                event
        );
    }
}
