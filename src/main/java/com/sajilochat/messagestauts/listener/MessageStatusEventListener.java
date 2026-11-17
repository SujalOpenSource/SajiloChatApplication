
package com.sajilochat.messagestatus.listener;

import com.sajilochat.messagestatus.event.MessageStatusDeliveryEvent;
import com.sajilochat.messagestatus.websocket.MessageStatusWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MessageStatusEventListener {

    private final MessageStatusWebSocketPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStatusUpdate(MessageStatusDeliveryEvent event) {
        publisher.publishStatusUpdate(
                event.senderUsername(),
                event.statusEvent()
        );
    }
}
