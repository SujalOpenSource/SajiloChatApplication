
package com.sajilochat.group.event;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GroupSystemMessageEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGroupSystemMessage(GroupSystemMessageEvent event) {

        String destination =
                "/topic/conversations/"
                        + event.conversationId()
                        + "/messages";

        messagingTemplate.convertAndSend(
                destination,
                event.message()
        );
    }
}
