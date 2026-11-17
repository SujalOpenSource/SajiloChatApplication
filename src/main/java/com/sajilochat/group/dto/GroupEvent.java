
package com.sajilochat.group.dto;

import com.sajilochat.group.enums.GroupRole;

import java.time.Instant;

public record GroupEvent(
        String eventType,
        Long groupId,
        String groupName,
        Long actorId,
        String actorUsername,
        Long targetUserId,
        String targetUsername,
        GroupRole role,
        Instant occurredAt
) {
}
