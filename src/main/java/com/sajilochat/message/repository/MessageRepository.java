
package com.sajilochat.message.repository;

import com.sajilochat.message.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            Long conversationId
    );

    Optional<Message> findByIdAndSenderIdAndDeletedAtIsNull(
            Long messageId,
            Long senderId
    );
}
