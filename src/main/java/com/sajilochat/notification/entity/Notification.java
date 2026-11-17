
package com.sajilochat.notification.entity;

import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.message.entity.Message;
import com.sajilochat.notification.enums.NotificationType;
import com.sajilochat.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(
                        name = "idx_notification_recipient_created",
                        columnList = "recipient_id, created_at"
                ),
                @Index(
                        name = "idx_notification_recipient_read",
                        columnList = "recipient_id, read_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private Message message;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    private Notification(
            User recipient,
            User actor,
            NotificationType type,
            Conversation conversation,
            Message message,
            String content
    ) {
        this.recipient = recipient;
        this.actor = actor;
        this.type = type;
        this.conversation = conversation;
        this.message = message;
        this.content = content;
    }

    public static Notification create(
            User recipient,
            User actor,
            NotificationType type,
            Conversation conversation,
            Message message,
            String content
    ) {
        return new Notification(
                recipient,
                actor,
                type,
                conversation,
                message,
                content
        );
    }

    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
