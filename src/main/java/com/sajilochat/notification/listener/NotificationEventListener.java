
package com.sajilochat.notification.listener;

import com.sajilochat.notification.event.NotificationCreatedEvent;
import com.sajilochat.notification.websocket.NotificationWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationWebSocketPublisher publisher;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleNotificationCreated(
            NotificationCreatedEvent event
    ) {

        log.info(
                "Database transaction committed. Publishing notification {}",
                event.notification().id()
        );

        publisher.publish(
                event.recipientUsername(),
                event.notification()
        );
    }
}
