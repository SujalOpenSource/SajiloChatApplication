
package com.sajilochat.presence.dto;

import java.time.Instant;
import java.util.List;

public record PresenceSnapshotResponse(
        Long conversationId,
        List<PresenceUserResponse> onlineUsers,
        Instant generatedAt
) {
}
