
package com.sajilochat.message.entity;

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
        name = "message_user_deletions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_message_user_deletion",
                        columnNames = {"message_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_message_user_deletion_user",
                        columnList = "user_id"
                )
        }
)
public class MessageUserDeletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "deleted_at", nullable = false, updatable = false)
    private Instant deletedAt;

    public MessageUserDeletion(Message message, User user) {
        this.message = message;
        this.user = user;
    }

    @PrePersist
    protected void onCreate() {
        this.deletedAt = Instant.now();
    }
}
