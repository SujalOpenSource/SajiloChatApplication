
package com.sajilochat.group.service;

import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.conversation.entity.ConversationMember;
import com.sajilochat.conversation.enums.ConversationType;
import com.sajilochat.conversation.repository.ConversationRepository;
import com.sajilochat.group.dto.AddGroupMembersRequest;
import com.sajilochat.group.dto.CreateGroupRequest;
import com.sajilochat.group.dto.GroupEvent;
import com.sajilochat.group.dto.GroupResponse;
import com.sajilochat.group.dto.UpdateGroupMemberRoleRequest;
import com.sajilochat.group.dto.UpdateGroupNameRequest;
import com.sajilochat.group.enums.GroupRole;
import com.sajilochat.group.event.GroupWebSocketDeliveryEvent;
import com.sajilochat.notification.enums.NotificationType;
import com.sajilochat.notification.service.NotificationService;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.enums.UserStatus;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher applicationEventPublisher;

    // CREATE GROUP

    @Transactional
    public GroupResponse createGroup(
            Long creatorId,
            CreateGroupRequest request
    ) {

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Group creator not found"
                        )
                );

        validateActiveUser(creator);

        String groupName = request.name().trim();

        if (groupName.isEmpty()) {
            throw new BadRequestException(
                    "Group name cannot be empty"
            );
        }

        List<Long> memberIds = request.memberIds();

        validateUniqueMemberIds(memberIds);

        if (memberIds.contains(creatorId)) {
            throw new BadRequestException(
                    "You cannot add yourself as another group member"
            );
        }

        List<User> users = userRepository.findAllById(memberIds);

        if (users.size() != memberIds.size()) {
            throw new ResourceNotFoundException(
                    "One or more requested users were not found"
            );
        }

        Map<Long, User> usersById = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        Function.identity()
                ));

        for (Long memberId : memberIds) {
            validateActiveUser(usersById.get(memberId));
        }

        Conversation conversation =
                Conversation.createGroup(creator, groupName);

        for (Long memberId : memberIds) {
            conversation.addMember(
                    usersById.get(memberId),
                    GroupRole.MEMBER
            );
        }

        Conversation savedConversation =
                conversationRepository.save(conversation);

        return mapToResponse(savedConversation);
    }

    // GET GROUP DETAILS

    public GroupResponse getGroup(
            Long groupId,
            Long currentUserId
    ) {

        Conversation conversation =
                findGroupForMember(groupId, currentUserId);

        return mapToResponse(conversation);
    }

    // ADD MEMBERS

    @Transactional
    public GroupResponse addMembers(
            Long groupId,
            Long currentUserId,
            AddGroupMembersRequest request
    ) {

        Conversation conversation =
                findGroupForMemberForUpdate(groupId, currentUserId);

        requireAdmin(conversation, currentUserId);

        User actor = findMember(conversation, currentUserId).getUser();

        List<Long> memberIds = request.memberIds();

        validateUniqueMemberIds(memberIds);

        if (memberIds.contains(currentUserId)) {
            throw new BadRequestException(
                    "You are already a member of this group"
            );
        }

        List<User> users = userRepository.findAllById(memberIds);

        if (users.size() != memberIds.size()) {
            throw new ResourceNotFoundException(
                    "One or more requested users were not found"
            );
        }

        Map<Long, User> usersById = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        Function.identity()
                ));

        List<User> addedUsers = new ArrayList<>();

        for (Long memberId : memberIds) {

            User user = usersById.get(memberId);

            validateActiveUser(user);

            boolean alreadyMember = conversation.getMembers()
                    .stream()
                    .anyMatch(member ->
                            member.getUser().getId().equals(memberId)
                    );

            if (!alreadyMember) {
                conversation.addMember(user, GroupRole.MEMBER);
                addedUsers.add(user);
            }
        }

        List<User> currentMembers = getCurrentMembers(conversation);

        for (User addedUser : addedUsers) {

            String content = actor.getUsername()
                    + " added "
                    + addedUser.getUsername()
                    + " to the group";

            notifyGroupMembers(
                    conversation,
                    actor,
                    NotificationType.GROUP_MEMBER_ADDED,
                    content,
                    currentMembers
            );

            publishGroupEvent(
                    conversation,
                    "GROUP_MEMBER_ADDED",
                    actor,
                    addedUser,
                    GroupRole.MEMBER,
                    currentMembers
            );
        }

        return mapToResponse(conversation);
    }

    // REMOVE MEMBER

    @Transactional
    public GroupResponse removeMember(
            Long groupId,
            Long currentUserId,
            Long targetUserId
    ) {

        Conversation conversation =
                findGroupForMemberForUpdate(groupId, currentUserId);

        requireAdmin(conversation, currentUserId);

        if (currentUserId.equals(targetUserId)) {
            throw new BadRequestException(
                    "Use the leave group operation to remove yourself"
            );
        }

        User actor = findMember(conversation, currentUserId).getUser();

        ConversationMember targetMember =
                findMember(conversation, targetUserId);

        if (targetMember.isAdmin()) {
            throw new BadRequestException(
                    "An admin cannot remove another admin"
            );
        }

        User targetUser = targetMember.getUser();

        conversation.removeMember(targetUserId);

        List<User> remainingMembers = getCurrentMembers(conversation);

        List<User> eventRecipients = new ArrayList<>(remainingMembers);
        eventRecipients.add(targetUser);

        String content = actor.getUsername()
                + " removed "
                + targetUser.getUsername()
                + " from the group";

        notifyGroupMembers(
                conversation,
                actor,
                NotificationType.GROUP_MEMBER_REMOVED,
                content,
                eventRecipients
        );

        publishGroupEvent(
                conversation,
                "GROUP_MEMBER_REMOVED",
                actor,
                targetUser,
                null,
                eventRecipients
        );

        return mapToResponse(conversation);
    }

    // RENAME GROUP

    @Transactional
    public GroupResponse updateGroupName(
            Long groupId,
            Long currentUserId,
            UpdateGroupNameRequest request
    ) {

        Conversation conversation =
                findGroupForMemberForUpdate(groupId, currentUserId);

        requireAdmin(conversation, currentUserId);

        User actor = findMember(conversation, currentUserId).getUser();

        String newName = request.name().trim();

        if (newName.isEmpty()) {
            throw new BadRequestException(
                    "Group name cannot be empty"
            );
        }

        if (newName.equals(conversation.getName())) {
            throw new BadRequestException(
                    "The group already has this name"
            );
        }

        conversation.updateName(newName);

        List<User> currentMembers = getCurrentMembers(conversation);

        String content = actor.getUsername()
                + " changed the group name to "
                + newName;

        notifyGroupMembers(
                conversation,
                actor,
                NotificationType.GROUP_NAME_UPDATED,
                content,
                currentMembers
        );

        publishGroupEvent(
                conversation,
                "GROUP_NAME_UPDATED",
                actor,
                null,
                null,
                currentMembers
        );

        return mapToResponse(conversation);
    }

    // PROMOTE OR DEMOTE MEMBER

    @Transactional
    public GroupResponse updateMemberRole(
            Long groupId,
            Long currentUserId,
            Long targetUserId,
            UpdateGroupMemberRoleRequest request
    ) {

        Conversation conversation =
                findGroupForMemberForUpdate(groupId, currentUserId);

        requireAdmin(conversation, currentUserId);

        User actor = findMember(conversation, currentUserId).getUser();

        ConversationMember targetMember =
                findMember(conversation, targetUserId);

        User targetUser = targetMember.getUser();

        GroupRole newRole = request.role();

        if (targetMember.getGroupRole() == newRole) {
            throw new BadRequestException(
                    "The member already has this role"
            );
        }

        if (targetMember.isAdmin()
                && newRole == GroupRole.MEMBER) {

            long adminCount = conversation.getMembers()
                    .stream()
                    .filter(ConversationMember::isAdmin)
                    .count();

            if (adminCount <= 1) {
                throw new BadRequestException(
                        "A group must have at least one admin"
                );
            }

            targetMember.demoteToMember();

        } else if (newRole == GroupRole.ADMIN) {

            targetMember.promoteToAdmin();

        } else {

            throw new BadRequestException(
                    "Invalid group role change"
            );
        }

        List<User> currentMembers = getCurrentMembers(conversation);

        String content = actor.getUsername()
                + " changed "
                + targetUser.getUsername()
                + "'s role to "
                + newRole.name();

        notifyGroupMembers(
                conversation,
                actor,
                NotificationType.GROUP_MEMBER_ROLE_UPDATED,
                content,
                currentMembers
        );

        publishGroupEvent(
                conversation,
                "GROUP_MEMBER_ROLE_UPDATED",
                actor,
                targetUser,
                newRole,
                currentMembers
        );

        return mapToResponse(conversation);
    }

    // LEAVE GROUP

    @Transactional
    public void leaveGroup(
            Long groupId,
            Long currentUserId
    ) {

        Conversation conversation =
                findGroupForMemberForUpdate(groupId, currentUserId);

        ConversationMember currentMember =
                findMember(conversation, currentUserId);

        User leavingUser = currentMember.getUser();

        if (currentMember.isAdmin()) {

            long adminCount = conversation.getMembers()
                    .stream()
                    .filter(ConversationMember::isAdmin)
                    .count();

            if (adminCount <= 1
                    && conversation.getMembers().size() > 1) {

                throw new BadRequestException(
                        "Promote another member to admin before leaving"
                );
            }
        }

        if (conversation.getMembers().size() == 1) {
            conversationRepository.delete(conversation);
            return;
        }

        conversation.removeMember(currentUserId);

        List<User> remainingMembers = getCurrentMembers(conversation);

        String content = leavingUser.getUsername()
                + " left the group";

        notifyGroupMembers(
                conversation,
                leavingUser,
                NotificationType.GROUP_MEMBER_LEFT,
                content,
                remainingMembers
        );

        List<User> eventRecipients = new ArrayList<>(remainingMembers);
        eventRecipients.add(leavingUser);

        publishGroupEvent(
                conversation,
                "GROUP_MEMBER_LEFT",
                leavingUser,
                leavingUser,
                null,
                eventRecipients
        );
    }

    // NOTIFY GROUP MEMBERS

    private void notifyGroupMembers(
            Conversation conversation,
            User actor,
            NotificationType type,
            String content,
            List<User> recipients
    ) {

        recipients.stream()
                .filter(user -> !user.getId().equals(actor.getId()))
                .collect(Collectors.toMap(
                        User::getId,
                        Function.identity(),
                        (first, second) -> first
                ))
                .values()
                .forEach(recipient ->
                        notificationService.createGroupNotification(
                                recipient,
                                actor,
                                type,
                                conversation,
                                content
                        )
                );
    }

    // PUBLISH REAL-TIME GROUP EVENTS

    private void publishGroupEvent(
            Conversation conversation,
            String eventType,
            User actor,
            User targetUser,
            GroupRole role,
            List<User> recipients
    ) {

        GroupEvent event = new GroupEvent(
                eventType,
                conversation.getId(),
                conversation.getName(),
                actor.getId(),
                actor.getUsername(),
                targetUser != null ? targetUser.getId() : null,
                targetUser != null ? targetUser.getUsername() : null,
                role,
                Instant.now()
        );

        recipients.stream()
                .map(User::getUsername)
                .collect(Collectors.toSet())
                .forEach(username ->
                        applicationEventPublisher.publishEvent(
                                new GroupWebSocketDeliveryEvent(
                                        username,
                                        event
                                )
                        )
                );
    }

    // GET CURRENT GROUP MEMBERS

    private List<User> getCurrentMembers(Conversation conversation) {

        return conversation.getMembers()
                .stream()
                .map(ConversationMember::getUser)
                .toList();
    }

    // FIND GROUP AND VERIFY MEMBERSHIP

    private Conversation findGroupForMember(
            Long groupId,
            Long userId
    ) {

        Conversation conversation =
                conversationRepository
                        .findByIdAndMemberUserId(groupId, userId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Group not found or you are not a member"
                                )
                        );

        validateGroupType(conversation);

        return conversation;
    }

    // FIND GROUP, VERIFY MEMBERSHIP AND LOCK FOR UPDATE

    private Conversation findGroupForMemberForUpdate(
            Long groupId,
            Long userId
    ) {

        Conversation conversation =
                conversationRepository
                        .findByIdAndMemberUserIdForUpdate(
                                groupId,
                                userId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Group not found or you are not a member"
                                )
                        );

        validateGroupType(conversation);

        return conversation;
    }

    private void validateGroupType(Conversation conversation) {

        if (conversation.getType() != ConversationType.GROUP) {
            throw new BadRequestException(
                    "This conversation is not a group"
            );
        }
    }

    // ADMIN AUTHORIZATION

    private void requireAdmin(
            Conversation conversation,
            Long userId
    ) {

        ConversationMember member =
                findMember(conversation, userId);

        if (!member.isAdmin()) {
            throw new BadRequestException(
                    "Only group admins can perform this operation"
            );
        }
    }

    // FIND MEMBER

    private ConversationMember findMember(
            Conversation conversation,
            Long userId
    ) {

        return conversation.getMembers()
                .stream()
                .filter(member ->
                        member.getUser().getId().equals(userId)
                )
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Group member not found"
                        )
                );
    }

    // VALIDATE ACTIVE USER

    private void validateActiveUser(User user) {

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException(
                    "Inactive users cannot participate in groups"
            );
        }
    }

    // VALIDATE DUPLICATE IDS

    private void validateUniqueMemberIds(List<Long> memberIds) {

        Set<Long> uniqueIds = new HashSet<>(memberIds);

        if (uniqueIds.size() != memberIds.size()) {
            throw new BadRequestException(
                    "Duplicate member IDs are not allowed"
            );
        }
    }

    // MAP ENTITY TO RESPONSE DTO

    private GroupResponse mapToResponse(
            Conversation conversation
    ) {

        List<GroupResponse.GroupMemberResponse> members =
                conversation.getMembers()
                        .stream()
                        .map(this::mapMemberToResponse)
                        .toList();

        return new GroupResponse(
                conversation.getId(),
                conversation.getName(),
                conversation.getCreatedBy().getId(),
                conversation.getCreatedBy().getUsername(),
                members.size(),
                conversation.getCreatedAt(),
                members
        );
    }


    private GroupResponse.GroupMemberResponse mapMemberToResponse(
            ConversationMember member
    ) {

        User user = member.getUser();

        return new GroupResponse.GroupMemberResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getProfilePictureUrl(),
                member.getGroupRole(),
                member.getJoinedAt()
        );
    }

}
