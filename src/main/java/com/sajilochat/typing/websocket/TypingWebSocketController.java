
package com.sajilochat.typing.websocket;

import com.sajilochat.block.repository.BlockRepository;
import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.conversation.enums.ConversationType;
import com.sajilochat.conversation.repository.ConversationRepository;
import com.sajilochat.security.UserPrincipal;
import com.sajilochat.typing.dto.TypingEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class TypingWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationRepository conversationRepository;
    private final BlockRepository blockRepository;

    @MessageMapping("/conversations/{conversationId}/typing")
    public void handleTypingEvent(
            @DestinationVariable Long conversationId,
            @Valid @Payload TypingEvent request,
            Principal principal
    ) {

        if (!(principal instanceof Authentication authentication)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "User is not authenticated"
            );
        }

        Object authenticatedPrincipal = authentication.getPrincipal();

        if (!(authenticatedPrincipal instanceof UserPrincipal userPrincipal)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authenticated user not found"
            );
        }

        if (request.conversationId() == null
                || !conversationId.equals(request.conversationId())) {
            throw new BadRequestException(
                    "Conversation ID does not match destination"
            );
        }

        Long userId = userPrincipal.getUserId();

        Conversation conversation = conversationRepository
                .findByIdAndMemberUserId(conversationId, userId)
                .orElseThrow(() -> new BadRequestException(
                        "You are not a member of this conversation"
                ));

        ensureNoBlockRelationship(conversation, userId);

        TypingNotification notification = new TypingNotification(
                userId,
                userPrincipal.getUsername(),
                conversationId,
                request.typing()
        );

        messagingTemplate.convertAndSend(
                "/topic/conversations/" + conversationId + "/typing",
                notification
        );
    }

    private void ensureNoBlockRelationship(
            Conversation conversation,
            Long userId
    ) {

        // Blocking rules apply only to private conversations.
        // Group typing events remain unaffected.
        if (conversation.getType() != ConversationType.PRIVATE) {
            return;
        }

        Long otherUserId = conversation.getMembers().stream()
                .filter(member ->
                        !member.getUser().getId().equals(userId)
                )
                .map(member -> member.getUser().getId())
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Private conversation participant could not be found"
                ));

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
                    "Typing indicators are unavailable because one of you has blocked the other"
            );
        }
    }

    public record TypingNotification(
            Long userId,
            String username,
            Long conversationId,
            Boolean typing
    ) {
    }
}
