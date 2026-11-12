
package com.sajilochat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Username or email is required")
        @Size(max = 100, message = "Username or email is too long")
        String identifier,

        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password is too long")
        String password

) {
}
