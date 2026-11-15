
package com.sajilochat.reaction.service;

import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.conversation.repository.ConversationRepository;
import com.sajilochat.message.entity.Message;
import com.sajilochat.message.repository.MessageRepository;
import com.sajilochat.reaction.dto.AddReactionRequest;
import com.sajilochat.reaction.dto.ReactionResponse;
import com.sajilochat.reaction.entity.MessageReaction;
import com.sajilochat.reaction.repository.MessageReactionRepository;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ReactionResponse addOrUpdateReaction(
            Long messageId,
            Long userId,
            AddReactionRequest request
    ) {

        Message message = getMessage(messageId);

        verifyConversationMembership(
                message.getConversation().getId(),
                userId
        );

        User user = getUser(userId);

        String emoji = request.emoji().trim();

        if (emoji.isEmpty()) {
            throw new IllegalArgumentException(
                    "Emoji cannot be empty"
            );
        }

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
                message.getConversation().getId(),
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reaction not found"
                ));

        ReactionResponse response = mapToResponse(reaction);

        reactionRepository.delete(reaction);

        publishReactionEvent(
                message.getConversation().getId(),
                "REMOVED",
                response
        );
    }

    private Message getMessage(Long messageId) {
        return messageRepository.findByIdAndDeletedAtIsNull(messageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Message not found"
                ));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found"
                ));
    }

    private void verifyConversationMembership(
            Long conversationId,
            Long userId
    ) {

        boolean isMember = conversationRepository
                .findByIdAndMemberUserId(conversationId, userId)
                .isPresent();

        if (!isMember) {
            throw new IllegalArgumentException(
                    "You are not a member of this conversation"
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

        ReactionEvent event = new ReactionEvent(
                action,
                reaction
        );

        messagingTemplate.convertAndSend(
                "/topic/conversations/" + conversationId + "/reactions",
                event
        );
    }

    public record ReactionEvent(
            String action,
            ReactionResponse reaction
    ) {
    }
}
