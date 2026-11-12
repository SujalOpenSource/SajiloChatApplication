
package com.sajilochat.auth.dto;

import com.sajilochat.user.enums.UserStatus;

import java.time.Instant;

public record RegisterResponse(
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UserStatus status,
        Instant createdAt
) {
}
