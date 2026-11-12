
package com.sajilochat.message.entity;

import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.message.enums.MessageType;
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
        name = "messages",
        indexes = {
                @Index(
                        name = "idx_messages_conversation_created",
                        columnList = "conversation_id, created_at"
                ),
                @Index(
                        name = "idx_messages_sender",
                        columnList = "sender_id"
                )
        }
)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type = MessageType.TEXT;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(name = "edited", nullable = false)
    private boolean edited = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Message createTextMessage(
            Conversation conversation,
            User sender,
            String content
    ) {
        Message message = new Message();
        message.conversation = conversation;
        message.sender = sender;
        message.type = MessageType.TEXT;
        message.content = content;

        return message;
    }

    public void editContent(String content) {
        if (deletedAt != null) {
            throw new IllegalStateException(
                    "Deleted messages cannot be edited"
            );
        }

        this.content = content;
        this.edited = true;
    }

    public void softDelete() {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
            this.content = "This message was deleted";
        }
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
