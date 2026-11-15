
package com.sajilochat.reaction.entity;

import com.sajilochat.message.entity.Message;
import com.sajilochat.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "message_reactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_message_reaction_user",
                        columnNames = {"message_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_message_reactions_message",
                        columnList = "message_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 32)
    private String emoji;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private MessageReaction(Message message, User user, String emoji) {
        this.message = message;
        this.user = user;
        this.emoji = emoji;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static MessageReaction create(
            Message message,
            User user,
            String emoji
    ) {
        return new MessageReaction(message, user, emoji);
    }

    public void changeEmoji(String emoji) {
        this.emoji = emoji;
        this.updatedAt = Instant.now();
    }
}
