
package com.sajilochat.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroupNameRequest(

        @NotBlank(message = "Group name is required")
        @Size(max = 100, message = "Group name cannot exceed 100 characters")
        String name

) {
}
