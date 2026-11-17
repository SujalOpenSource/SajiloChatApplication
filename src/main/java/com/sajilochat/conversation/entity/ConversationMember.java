
package com.sajilochat.conversation.entity;

import com.sajilochat.group.enums.GroupRole;
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
        name = "conversation_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_conversation_member",
                        columnNames = {"conversation_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_conversation_members_user",
                        columnList = "user_id"
                )
        }
)
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_role", length = 20)
    private GroupRole groupRole;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    public ConversationMember(
            Conversation conversation,
            User user,
            GroupRole groupRole
    ) {
        this.conversation = conversation;
        this.user = user;
        this.groupRole = groupRole;
    }

    public void updateLastReadAt(Instant lastReadAt) {
        this.lastReadAt = lastReadAt;
    }

    public boolean isAdmin() {
        return groupRole == GroupRole.ADMIN;
    }

    public void promoteToAdmin() {
        this.groupRole = GroupRole.ADMIN;
    }

    public void demoteToMember() {
        this.groupRole = GroupRole.MEMBER;
    }

    @PrePersist
    protected void onCreate() {
        this.joinedAt = Instant.now();
    }
}
