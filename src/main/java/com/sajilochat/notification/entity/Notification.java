
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(
                        name = "idx_notifications_recipient_created",
                        columnList = "recipient_id, created_at"
                ),
                @Index(
                        name = "idx_notifications_recipient_read",
                        columnList = "recipient_id, read_at"
                )
        }
)
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
    @Column(nullable = false, length = 30)
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

    public static Notification create(
            User recipient,
            User actor,
            NotificationType type,
            Conversation conversation,
            Message message,
            String content
    ) {
        Notification notification = new Notification();

        notification.recipient = recipient;
        notification.actor = actor;
        notification.type = type;
        notification.conversation = conversation;
        notification.message = message;
        notification.content = content;

        return notification;
    }

    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
    }

    public boolean isRead() {
        return this.readAt != null;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
