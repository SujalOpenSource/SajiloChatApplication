
package com.sajilochat.reaction.listener;

import com.sajilochat.reaction.event.ReactionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReactionEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleReactionEvent(ReactionEvent event) {

        messagingTemplate.convertAndSend(
                "/topic/conversations/"
                        + event.conversationId()
                        + "/reactions",
                event
        );
    }
}
