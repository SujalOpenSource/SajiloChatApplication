
package com.sajilochat.group.dto;

import com.sajilochat.group.enums.GroupRole;

import java.time.Instant;
import java.util.List;

public record GroupResponse(

        Long id,

        String name,

        Long createdById,

        String createdByUsername,

        int memberCount,

        Instant createdAt,

        List<GroupMemberResponse> members

) {

    public record GroupMemberResponse(

            Long userId,

            String username,

            String firstName,

            String lastName,

            String profilePictureUrl,

            GroupRole role,

            Instant joinedAt

    ) {
    }
}
