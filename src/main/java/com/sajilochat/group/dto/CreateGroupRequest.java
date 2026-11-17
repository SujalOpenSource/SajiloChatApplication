
package com.sajilochat.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGroupRequest(

        @NotBlank(message = "Group name is required")
        @Size(max = 100, message = "Group name cannot exceed 100 characters")
        String name,

        @NotEmpty(message = "At least one member is required")
        List<
                @NotNull(message = "Member ID cannot be null")
                @Positive(message = "Member ID must be positive")
                        Long
                > memberIds

) {
}
