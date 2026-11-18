
package com.sajilochat.messagestatus.repository;

import com.sajilochat.messagestatus.entity.MessageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageStatusRepository
        extends JpaRepository<MessageStatus, Long> {

    Optional<MessageStatus> findByMessageIdAndRecipientId(
            Long messageId,
            Long recipientId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ms
            FROM MessageStatus ms
            WHERE ms.message.id = :messageId
              AND ms.recipient.id = :recipientId
            """)
    Optional<MessageStatus> findByMessageIdAndRecipientIdForUpdate(
            @Param("messageId") Long messageId,
            @Param("recipientId") Long recipientId
    );

    List<MessageStatus> findAllByMessageId(Long messageId);

    List<MessageStatus> findAllByRecipientIdAndStatus(
            Long recipientId,
            com.sajilochat.messagestatus.enums.DeliveryStatus status
    );
}
