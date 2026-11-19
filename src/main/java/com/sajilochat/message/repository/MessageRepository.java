
package com.sajilochat.message.repository;

import com.sajilochat.message.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import com.sajilochat.message.entity.Message;
import java.time.Instant;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
            SELECT m
            FROM Message m
            WHERE m.conversation.id = :conversationId
              AND m.deletedAt IS NULL
              AND NOT EXISTS (
                  SELECT d.id
                  FROM MessageUserDeletion d
                  WHERE d.message = m
                    AND d.user.id = :userId
              )
            ORDER BY m.createdAt ASC
            """)
    List<Message> findVisibleMessages(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId
    );


    List<Message> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            Long conversationId
    );

    List<Message> findByConversationIdAndDeletedAtIsNullAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            Long conversationId,
            Instant joinedAt
    );
    Optional<Message> findByIdAndSenderIdAndDeletedAtIsNull(
            Long messageId,
            Long senderId
    );

    Optional<Message> findByIdAndDeletedAtIsNull(Long id);
}
