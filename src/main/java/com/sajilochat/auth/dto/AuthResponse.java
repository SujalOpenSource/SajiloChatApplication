
package com.sajilochat.auth.dto;

import com.sajilochat.user.enums.UserStatus;

public record AuthResponse(

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
