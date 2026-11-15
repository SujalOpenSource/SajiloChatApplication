
package com.sajilochat.notification.repository;

import com.sajilochat.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
