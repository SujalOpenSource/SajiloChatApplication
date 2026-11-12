
package com.sajilochat.auth.dto;

import com.sajilochat.user.enums.UserStatus;

import java.time.Instant;

public record AuthResponse(

        String tokenType,

        String accessToken,

        long expiresIn,

        AuthenticatedUser user

) {

    public record AuthenticatedUser(
            Long id,
            String username,
            String firstName,
            String lastName,
            String email,
            UserStatus status
    ) {
    }
}
