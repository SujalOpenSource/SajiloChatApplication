
package com.sajilochat.presence.dto;

import com.sajilochat.presence.enums.PresenceStatus;

public record PresenceUserResponse(
        Long userId,
        String username,
        PresenceStatus status
) {
}
