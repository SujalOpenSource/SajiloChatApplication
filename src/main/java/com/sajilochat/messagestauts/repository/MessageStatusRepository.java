
package com.sajilochat.messagestatus.repository;

import com.sajilochat.messagestatus.entity.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageStatusRepository
        extends JpaRepository<MessageStatus, Long> {

    Optional<MessageStatus> findByMessageIdAndRecipientId(
            Long messageId,
            Long recipientId
    );

    List<MessageStatus> findAllByMessageId(Long messageId);

    List<MessageStatus> findAllByRecipientIdAndStatus(
            Long recipientId,
            com.sajilochat.messagestatus.enums.DeliveryStatus status
    );
}
