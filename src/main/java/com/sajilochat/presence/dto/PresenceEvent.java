
package com.sajilochat.presence.dto;

import com.sajilochat.presence.enums.PresenceStatus;

import java.time.Instant;

public record PresenceEvent(
        Long userId,
        String username,
        PresenceStatus status,
        Instant lastSeenAt
) {
}
