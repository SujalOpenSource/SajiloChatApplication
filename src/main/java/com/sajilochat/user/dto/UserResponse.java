
package com.sajilochat.user.dto;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String firstName,
        String lastName,
        String profilePictureUrl,
        String bio,
        Instant lastSeenAt
) {}
