
package com.sajilochat.group.event;

import com.sajilochat.group.websocket.GroupWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GroupWebSocketEventListener {

    private final GroupWebSocketPublisher groupWebSocketPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGroupEvent(GroupWebSocketDeliveryEvent event) {

        groupWebSocketPublisher.publish(
                event.recipientUsername(),
                event.groupEvent()
        );
    }
}
