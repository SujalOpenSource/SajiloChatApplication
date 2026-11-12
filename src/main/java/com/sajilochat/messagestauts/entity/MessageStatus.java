
package com.sajilochat.messagestatus.entity;

import com.sajilochat.messagestatus.enums.DeliveryStatus;
import com.sajilochat.message.entity.Message;
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
        name = "message_statuses",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_message_status_message_user",
                        columnNames = {"message_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_message_status_user_status",
                        columnList = "user_id, status"
                )
        }
)
public class MessageStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static MessageStatus create(Message message, User recipient) {
        MessageStatus messageStatus = new MessageStatus();
        messageStatus.message = message;
        messageStatus.recipient = recipient;
        messageStatus.status = DeliveryStatus.SENT;
        return messageStatus;
    }

    public void markAsDelivered() {
        if (status == DeliveryStatus.SENT) {
            status = DeliveryStatus.DELIVERED;
            deliveredAt = Instant.now();
        }
    }

    public void markAsRead() {
        if (status != DeliveryStatus.READ) {
            if (deliveredAt == null) {
                deliveredAt = Instant.now();
            }

            status = DeliveryStatus.READ;
            readAt = Instant.now();
        }
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
