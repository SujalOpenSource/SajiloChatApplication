
package com.sajilochat.conversation.repository;

import com.sajilochat.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByPrivateConversationKey(
            String privateConversationKey
    );

    @Query("""
            SELECT DISTINCT c
            FROM Conversation c
            JOIN c.members m
            WHERE m.user.id = :userId
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findAllByMemberUserId(
            @Param("userId") Long userId
    );

    @Query("""
            SELECT c
            FROM Conversation c
            JOIN c.members m
            WHERE c.id = :conversationId
            AND m.user.id = :userId
            """)
    Optional<Conversation> findByIdAndMemberUserId(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId
    );
}
