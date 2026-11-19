
package com.sajilochat.message.service;

import com.sajilochat.block.repository.BlockRepository;
import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.conversation.enums.ConversationType;
import com.sajilochat.conversation.repository.ConversationRepository;
import com.sajilochat.message.dto.EditMessageRequest;
import com.sajilochat.message.dto.MessageResponse;
import com.sajilochat.message.dto.SendMessageRequest;
import com.sajilochat.message.entity.Message;
import com.sajilochat.message.entity.MessageUserDeletion;
import com.sajilochat.message.repository.MessageRepository;
import com.sajilochat.message.repository.MessageUserDeletionRepository;
import com.sajilochat.messagestatus.service.MessageStatusService;
import com.sajilochat.notification.service.NotificationService;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageUserDeletionRepository messageUserDeletionRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MessageStatusService messageStatusService;
    private final NotificationService notificationService;
    private final BlockRepository blockRepository;

    @Transactional
    public MessageResponse sendMessage(
            Long conversationId,
            Long senderId,
            SendMessageRequest request
    ) {
        Conversation conversation =
                findConversationForMember(conversationId, senderId);

        User sender = userRepository.findById(senderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        if (conversation.getType() == ConversationType.PRIVATE) {
            ensureNoBlockRelationship(conversation, senderId);
        }

        if (request.content() == null ||
                request.content().trim().isEmpty()) {
            throw new BadRequestException(
                    "Message content cannot be empty"
            );
        }

        String content = request.content().trim();

        Message message = Message.createTextMessage(
                conversation,
                sender,
                content
        );

        Message savedMessage = messageRepository.save(message);

        messageStatusService.createStatusesForMessage(savedMessage);

        conversation.updateLastMessageAt(Instant.now());

        notificationService.createNewMessageNotifications(savedMessage);

        return mapToResponse(savedMessage);
    }

    /**
     * Retrieves messages visible to the authenticated user.
     *
     * Private conversations retain their existing history.
     * Group conversations only return messages created after
     * the user's current membership started.
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(
            Long conversationId,
            Long userId
    ) {
        Conversation conversation =
                findConversationForMember(conversationId, userId);

        // Private conversations retain their existing full history.
        if (conversation.getType() == ConversationType.PRIVATE) {
            return messageRepository
                    .findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                            conversationId
                    )
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        }

        // Find the user's current group membership.
        Instant joinedAt = conversation.getMembers()
                .stream()
                .filter(member ->
                        member.getUser().getId().equals(userId)
                )
                .map(member -> member.getJoinedAt())
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Group membership not found"
                        )
                );

        // Only show messages from the current membership period.
        return messageRepository
                .findByConversationIdAndDeletedAtIsNullAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        conversationId,
                        joinedAt
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public MessageResponse editMessage(
            Long messageId,
            Long userId,
            EditMessageRequest request
    ) {
        Message message = messageRepository
                .findByIdAndSenderIdAndDeletedAtIsNull(messageId, userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Message not found or you are not its sender"
                        )
                );

        findConversationForMember(
                message.getConversation().getId(),
                userId
        );

        if (request.content() == null ||
                request.content().trim().isEmpty()) {
            throw new BadRequestException(
                    "Message content cannot be empty"
            );
        }

        String content = request.content().trim();

        message.editContent(content);

        return mapToResponse(message);
    }

    /**
     * Deletes a message for everyone.
     * Only its original sender can perform this operation.
     */
    @Transactional
    public void deleteMessage(Long messageId, Long userId) {

        Message message = messageRepository
                .findByIdAndSenderIdAndDeletedAtIsNull(messageId, userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Message not found or you are not its sender"
                        )
                );

        findConversationForMember(
                message.getConversation().getId(),
                userId
        );

        message.softDelete();
    }

    /**
     * Hides a message only for the authenticated user.
     */
    @Transactional
    public void deleteMessageForMe(Long messageId, Long userId) {

        Message message = messageRepository
                .findByIdAndDeletedAtIsNull(messageId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Message not found"
                        )
                );

        findConversationForMember(
                message.getConversation().getId(),
                userId
        );

        if (messageUserDeletionRepository
                .existsByMessageIdAndUserId(messageId, userId)) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        messageUserDeletionRepository.save(
                new MessageUserDeletion(message, user)
        );
    }

    private void ensureNoBlockRelationship(
            Conversation conversation,
            Long senderId
    ) {
        Long recipientId = conversation.getMembers()
                .stream()
                .filter(member ->
                        !member.getUser().getId().equals(senderId)
                )
                .map(member -> member.getUser().getId())
                .findFirst()
                .orElseThrow(() ->
                        new BadRequestException(
                                "Private conversation participant could not be found"
                        )
                );

        boolean blockedBySender =
                blockRepository.existsByBlockerIdAndBlockedId(
                        senderId,
                        recipientId
                );

        boolean blockedByRecipient =
                blockRepository.existsByBlockerIdAndBlockedId(
                        recipientId,
                        senderId
                );

        if (blockedBySender || blockedByRecipient) {
            throw new BadRequestException(
                    "You cannot send messages in this conversation because one of you has blocked the other"
            );
        }
    }

    private Conversation findConversationForMember(
            Long conversationId,
            Long userId
    ) {
        return conversationRepository
                .findByIdAndMemberUserId(conversationId, userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Conversation not found"
                        )
                );
    }

    private MessageResponse mapToResponse(Message message) {

        User sender = message.getSender();

        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                new MessageResponse.SenderResponse(
                        sender.getId(),
                        sender.getUsername(),
                        sender.getFirstName(),
                        sender.getLastName(),
                        sender.getProfilePictureUrl()
                ),
                message.getType(),
                message.getContent(),
                message.isEdited(),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
}
