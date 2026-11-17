
package com.sajilochat.reaction.service;

import com.sajilochat.block.repository.BlockRepository;
import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.conversation.enums.ConversationType;
import com.sajilochat.conversation.repository.ConversationRepository;
import com.sajilochat.message.entity.Message;
import com.sajilochat.message.repository.MessageRepository;
import com.sajilochat.reaction.dto.AddReactionRequest;
import com.sajilochat.reaction.dto.ReactionResponse;
import com.sajilochat.reaction.entity.MessageReaction;
import com.sajilochat.reaction.event.ReactionEvent;
import com.sajilochat.reaction.repository.MessageReactionRepository;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReactionService {

    private final MessageReactionRepository reactionRepository;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReactionResponse addOrUpdateReaction(
            Long messageId,
            Long userId,
            AddReactionRequest request
    ) {

        Message message = getMessage(messageId);

        Conversation conversation = message.getConversation();

        verifyConversationMembership(
                conversation.getId(),
                userId
        );

        ensureNoBlockRelationship(conversation, userId);

        User user = getUser(userId);

        if (request.emoji() == null || request.emoji().isBlank()) {
            throw new BadRequestException("Emoji cannot be empty");
        }

        String emoji = request.emoji().trim();

        MessageReaction reaction = reactionRepository
                .findByMessageIdAndUserId(messageId, userId)
                .map(existingReaction -> {
                    existingReaction.changeEmoji(emoji);
                    return existingReaction;
                })
                .orElseGet(() ->
                        MessageReaction.create(message, user, emoji)
                );

        MessageReaction savedReaction =
                reactionRepository.save(reaction);

        ReactionResponse response = mapToResponse(savedReaction);

        publishReactionEvent(
                conversation.getId(),
                "ADDED_OR_UPDATED",
                response
        );

        return response;
    }

    public List<ReactionResponse> getMessageReactions(
            Long messageId,
            Long userId
    ) {

        Message message = getMessage(messageId);

        verifyConversationMembership(
                message.getConversation().getId(),
                userId
        );

        return reactionRepository
                .findAllByMessageIdOrderByCreatedAtAsc(messageId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void removeReaction(
            Long messageId,
            Long userId
    ) {

        Message message = getMessage(messageId);

        verifyConversationMembership(
                message.getConversation().getId(),
                userId
        );

        MessageReaction reaction = reactionRepository
                .findByMessageIdAndUserId(messageId, userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Reaction not found"
                        )
                );

        ReactionResponse response = mapToResponse(reaction);

        reactionRepository.delete(reaction);

        publishReactionEvent(
                message.getConversation().getId(),
                "REMOVED",
                response
        );
    }

    private void ensureNoBlockRelationship(
            Conversation conversation,
            Long userId
    ) {

        if (conversation.getType() != ConversationType.PRIVATE) {
            return;
        }

        Long otherUserId = conversation.getMembers().stream()
                .filter(member ->
                        !member.getUser().getId().equals(userId)
                )
                .map(member -> member.getUser().getId())
                .findFirst()
                .orElseThrow(() ->
                        new BadRequestException(
                                "Private conversation participant could not be found"
                        )
                );

        boolean blockedByCurrentUser =
                blockRepository.existsByBlockerIdAndBlockedId(
                        userId,
                        otherUserId
                );

        boolean blockedByOtherUser =
                blockRepository.existsByBlockerIdAndBlockedId(
                        otherUserId,
                        userId
                );

        if (blockedByCurrentUser || blockedByOtherUser) {
            throw new BadRequestException(
                    "Reactions are unavailable because one of you has blocked the other"
            );
        }
    }

    private Message getMessage(Long messageId) {

        return messageRepository.findByIdAndDeletedAtIsNull(messageId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Message not found"
                        )
                );
    }

    private User getUser(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );
    }

    private void verifyConversationMembership(
            Long conversationId,
            Long userId
    ) {

        boolean isMember = conversationRepository
                .findByIdAndMemberUserId(conversationId, userId)
                .isPresent();

        if (!isMember) {
            throw new ResourceNotFoundException(
                    "Conversation not found or you are not a member"
            );
        }
    }

    private ReactionResponse mapToResponse(
            MessageReaction reaction
    ) {

        return new ReactionResponse(
                reaction.getId(),
                reaction.getMessage().getId(),
                reaction.getMessage().getConversation().getId(),
                reaction.getUser().getId(),
                reaction.getUser().getUsername(),
                reaction.getEmoji(),
                reaction.getCreatedAt(),
                reaction.getUpdatedAt()
        );
    }

    private void publishReactionEvent(
            Long conversationId,
            String action,
            ReactionResponse reaction
    ) {

        eventPublisher.publishEvent(
                new ReactionEvent(
                        conversationId,
                        action,
                        reaction
                )
        );
    }
}
