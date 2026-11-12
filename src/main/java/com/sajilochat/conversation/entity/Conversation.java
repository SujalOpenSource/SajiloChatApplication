
package com.sajilochat.conversation.entity;

import com.sajilochat.conversation.enums.ConversationType;
import com.sajilochat.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "conversations",
        indexes = {
                @Index(name = "idx_conversations_type", columnList = "type"),
                @Index(name = "idx_conversations_updated_at", columnList = "updated_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_conversations_private_key",
                        columnNames = "private_conversation_key"
                )
        }
)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationType type;

    @Column(length = 100)
    private String name;

    @Column(name = "private_conversation_key", length = 50)
    private String privateConversationKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(
            mappedBy = "conversation",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("joinedAt ASC")
    private List<ConversationMember> members = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    public static Conversation createPrivate(
            User firstUser,
            User secondUser
    ) {
        Conversation conversation = new Conversation();

        conversation.type = ConversationType.PRIVATE;
        conversation.createdBy = firstUser;

        Long lowerId = Math.min(firstUser.getId(), secondUser.getId());
        Long higherId = Math.max(firstUser.getId(), secondUser.getId());

        conversation.privateConversationKey =
                lowerId + ":" + higherId;

        conversation.addMember(firstUser);
        conversation.addMember(secondUser);

        return conversation;
    }

    public static Conversation createGroup(
            User creator,
            String name
    ) {
        Conversation conversation = new Conversation();

        conversation.type = ConversationType.GROUP;
        conversation.createdBy = creator;
        conversation.name = name;

        conversation.addMember(creator);

        return conversation;
    }

    public void addMember(User user) {
        boolean alreadyMember = members.stream()
                .anyMatch(member ->
                        member.getUser().getId().equals(user.getId())
                );

        if (!alreadyMember) {
            members.add(new ConversationMember(this, user));
        }
    }

    public void updateLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
        this.updatedAt = lastMessageAt;
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
