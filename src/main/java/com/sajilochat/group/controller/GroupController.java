
package com.sajilochat.group.controller;

import com.sajilochat.group.dto.AddGroupMembersRequest;
import com.sajilochat.group.dto.CreateGroupRequest;
import com.sajilochat.group.dto.GroupResponse;
import com.sajilochat.group.dto.UpdateGroupMemberRoleRequest;
import com.sajilochat.group.dto.UpdateGroupNameRequest;
import com.sajilochat.group.service.GroupService;
import com.sajilochat.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    // CREATE GROUP

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateGroupRequest request
    ) {

        return groupService.createGroup(
                principal.getUserId(),
                request
        );
    }

    // GET GROUP DETAILS

    @GetMapping("/{groupId}")
    public GroupResponse getGroup(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {

        return groupService.getGroup(
                groupId,
                principal.getUserId()
        );
    }

    // ADD MEMBERS

    @PostMapping("/{groupId}/members")
    public GroupResponse addMembers(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddGroupMembersRequest request
    ) {

        return groupService.addMembers(
                groupId,
                principal.getUserId(),
                request
        );
    }

    // REMOVE MEMBER

    @DeleteMapping("/{groupId}/members/{userId}")
    public GroupResponse removeMember(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {

        return groupService.removeMember(
                groupId,
                principal.getUserId(),
                userId
        );
    }

    // UPDATE GROUP NAME

    @PatchMapping("/{groupId}/name")
    public GroupResponse updateGroupName(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateGroupNameRequest request
    ) {

        return groupService.updateGroupName(
                groupId,
                principal.getUserId(),
                request
        );
    }

    // UPDATE MEMBER ROLE

    @PatchMapping("/{groupId}/members/{userId}/role")
    public GroupResponse updateMemberRole(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateGroupMemberRoleRequest request
    ) {

        return groupService.updateMemberRole(
                groupId,
                principal.getUserId(),
                userId,
                request
        );
    }

    // LEAVE GROUP

    @PostMapping("/{groupId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveGroup(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {

        groupService.leaveGroup(
                groupId,
                principal.getUserId()
        );
    }
}
