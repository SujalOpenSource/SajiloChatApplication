
package com.sajilochat.messagestatus.service;

import com.sajilochat.block.repository.BlockRepository;
import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.conversation.enums.ConversationType;
import com.sajilochat.message.entity.Message;
import com.sajilochat.message.repository.MessageRepository;
import com.sajilochat.messagestatus.dto.MessageStatusResponse;
import com.sajilochat.messagestatus.entity.MessageStatus;
import com.sajilochat.messagestatus.repository.MessageStatusRepository;
import com.sajilochat.messagestatus.websocket.MessageStatusWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageStatusService {

    private final MessageStatusRepository messageStatusRepository;
    private final MessageRepository messageRepository;
    private final MessageStatusWebSocketPublisher messageStatusWebSocketPublisher;
    private final BlockRepository blockRepository;

    @Transactional
    public void createStatusesForMessage(Message message) {

        List<MessageStatus> statuses = message.getConversation()
                .getMembers()
                .stream()
                .map(member -> member.getUser())
                .filter(user ->
                        !user.getId().equals(message.getSender().getId())
                )
                .map(user -> MessageStatus.create(message, user))
                .toList();

        if (!statuses.isEmpty()) {
            messageStatusRepository.saveAll(statuses);
        }
    }

    public List<MessageStatusResponse> getMessageStatuses(
            Long messageId,
            Long currentUserId
    ) {

        Message message = findMessage(messageId);

        // Only the original sender can view recipient statuses.
        if (!message.getSender().getId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Message not found");
        }

        return messageStatusRepository.findAllByMessageId(messageId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public MessageStatusResponse markAsDelivered(
            Long messageId,
            Long currentUserId
    ) {

        MessageStatus status = findRecipientStatus(
                messageId,
                currentUserId
        );

        ensureNoBlockRelationship(
                status.getMessage().getConversation(),
                currentUserId
        );

        status.markAsDelivered();

        messageStatusWebSocketPublisher.publishStatusUpdate(status);

        return mapToResponse(status);
    }

    @Transactional
    public MessageStatusResponse markAsRead(
            Long messageId,
            Long currentUserId
    ) {

        MessageStatus status = findRecipientStatus(
                messageId,
                currentUserId
        );

        ensureNoBlockRelationship(
                status.getMessage().getConversation(),
                currentUserId
        );

        status.markAsRead();

        messageStatusWebSocketPublisher.publishStatusUpdate(status);

        return mapToResponse(status);
    }

    private void ensureNoBlockRelationship(
            Conversation conversation,
            Long currentUserId
    ) {

        // Group message receipts remain unaffected.
        if (conversation.getType() != ConversationType.PRIVATE) {
            return;
        }

        Long otherUserId = conversation.getMembers()
                .stream()
                .filter(member ->
                        !member.getUser().getId().equals(currentUserId)
                )
                .map(member -> member.getUser().getId())
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Private conversation participant could not be found"
                ));

        boolean blockedByCurrentUser =
                blockRepository.existsByBlockerIdAndBlockedId(
                        currentUserId,
                        otherUserId
                );

        boolean blockedByOtherUser =
                blockRepository.existsByBlockerIdAndBlockedId(
                        otherUserId,
                        currentUserId
                );

        if (blockedByCurrentUser || blockedByOtherUser) {
            throw new BadRequestException(
                    "Message status updates are unavailable because one of you has blocked the other"
            );
        }
    }

    private MessageStatus findRecipientStatus(
            Long messageId,
            Long currentUserId
    ) {

        return messageStatusRepository
                .findByMessageIdAndRecipientId(messageId, currentUserId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Message status not found"
                        )
                );
    }

    private Message findMessage(Long messageId) {

        return messageRepository.findById(messageId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Message not found")
                );
    }

    private MessageStatusResponse mapToResponse(MessageStatus status) {

        return new MessageStatusResponse(
                status.getId(),
                status.getMessage().getId(),
                status.getRecipient().getId(),
                status.getRecipient().getUsername(),
                status.getStatus(),
                status.getDeliveredAt(),
                status.getReadAt()
        );
    }
}
