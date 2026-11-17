
package com.sajilochat.block.entity;

import com.sajilochat.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "user_blocks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_block_blocker_blocked",
                        columnNames = {"blocker_id", "blocked_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_user_blocks_blocker",
                        columnList = "blocker_id"
                ),
                @Index(
                        name = "idx_user_blocks_blocked",
                        columnList = "blocked_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "blocker_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_blocks_blocker")
    )
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "blocked_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_blocks_blocked")
    )
    private User blocked;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private UserBlock(User blocker, User blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
        this.createdAt = Instant.now();
    }

    public static UserBlock create(User blocker, User blocked) {
        if (blocker == null || blocked == null) {
            throw new IllegalArgumentException(
                    "Both blocker and blocked users are required"
            );
        }

        if (blocker.getId().equals(blocked.getId())) {
            throw new IllegalArgumentException(
                    "A user cannot block themselves"
            );
        }

        return new UserBlock(blocker, blocked);
    }
}
