
package com.sajilochat.notification.repository;

import com.sajilochat.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByRecipientIdOrderByCreatedAtDesc(
            Long recipientId,
            Pageable pageable
    );

    Page<Notification> findAllByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(
            Long recipientId,
            Pageable pageable
    );

    Optional<Notification> findByIdAndRecipientId(
            Long notificationId,
            Long recipientId
    );

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            UPDATE Notification n
            SET n.readAt = :readAt
            WHERE n.recipient.id = :userId
              AND n.readAt IS NULL
            """)
    int markAllUnreadAsRead(
            @Param("userId") Long userId,
            @Param("readAt") Instant readAt
    );
}
