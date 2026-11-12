
package com.sajilochat.user.dto;

import com.sajilochat.user.enums.UserStatus;

import java.time.Instant;

public record UserProfileResponse(
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String profilePictureUrl,
        String bio,
        UserStatus status,
        Instant lastSeenAt,
        Instant createdAt
) {}
