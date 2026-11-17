
package com.sajilochat.group.dto;

import com.sajilochat.group.enums.GroupRole;
import jakarta.validation.constraints.NotNull;

public record UpdateGroupMemberRoleRequest(

        @NotNull(message = "Group role is required")
        GroupRole role

) {
}
