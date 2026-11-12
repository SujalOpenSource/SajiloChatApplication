
package com.sajilochat.conversation.service;

import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.conversation.dto.ConversationResponse;
import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.conversation.entity.ConversationMember;
import com.sajilochat.conversation.repository.ConversationRepository;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.enums.UserStatus;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    @Transactional
    public ConversationResponse createPrivateConversation(
            Long currentUserId,
            Long targetUserId
    ) {
        if (currentUserId.equals(targetUserId)) {
            throw new BadRequestException(
                    "You cannot create a conversation with yourself"
            );
        }

        User currentUser = findActiveUser(currentUserId);
        User targetUser = findActiveUser(targetUserId);

        Long lowerId = Math.min(currentUserId, targetUserId);
        Long higherId = Math.max(currentUserId, targetUserId);

        String privateKey = lowerId + ":" + higherId;

        Conversation conversation = conversationRepository
                .findByPrivateConversationKey(privateKey)
                .orElseGet(() -> {
                    Conversation newConversation =
                            Conversation.createPrivate(currentUser, targetUser);

                    return conversationRepository.save(newConversation);
                });

        return mapToResponse(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(Long userId) {

        findActiveUser(userId);

        return conversationRepository.findAllByMemberUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(
            Long conversationId,
            Long userId
    ) {
        findActiveUser(userId);

        Conversation conversation = conversationRepository
                .findByIdAndMemberUserId(conversationId, userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Conversation not found"
                        )
                );

        return mapToResponse(conversation);
    }

    private User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException(
                    "This user account is not active"
            );
        }

        return user;
    }

    private ConversationResponse mapToResponse(
            Conversation conversation
    ) {
        List<ConversationResponse.MemberResponse> members =
                conversation.getMembers()
                        .stream()
                        .map(this::mapMember)
                        .toList();

        return new ConversationResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getName(),
                conversation.getCreatedBy().getId(),
                members,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                conversation.getLastMessageAt()
        );
    }

    private ConversationResponse.MemberResponse mapMember(
            ConversationMember member
    ) {
        User user = member.getUser();

        return new ConversationResponse.MemberResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                member.getJoinedAt()
        );
    }
}
