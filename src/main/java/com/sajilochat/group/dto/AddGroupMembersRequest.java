
package com.sajilochat.group.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record AddGroupMembersRequest(

        @NotEmpty(message = "At least one member is required")
        List<
                @NotNull(message = "Member ID cannot be null")
                @Positive(message = "Member ID must be positive")
                        Long
                > memberIds

) {
}
