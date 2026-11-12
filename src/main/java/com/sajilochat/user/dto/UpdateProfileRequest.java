
package com.sajilochat.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank(message = "First name cannot be blank")
        @Size(max = 50, message = "First name cannot exceed 50 characters")
        String firstName,

        @NotBlank(message = "Last name cannot be blank")
        @Size(max = 50, message = "Last name cannot exceed 50 characters")
        String lastName,

        @Size(max = 250, message = "Bio cannot exceed 250 characters")
        String bio,

        @Size(max = 500, message = "Profile picture URL cannot exceed 500 characters")
        String profilePictureUrl
) {}
