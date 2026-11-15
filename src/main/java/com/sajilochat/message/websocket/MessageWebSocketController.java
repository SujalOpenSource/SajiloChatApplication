
package com.sajilochat.message.websocket;

import com.sajilochat.message.dto.MessageResponse;
import com.sajilochat.message.dto.SendMessageRequest;
import com.sajilochat.message.service.MessageService;
import com.sajilochat.security.UserPrincipal;
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
public class MessageWebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/conversations/{conversationId}/messages")
    public void sendMessage(
            @DestinationVariable Long conversationId,
            @Valid @Payload SendMessageRequest request,
            Principal principal
    ) {

        // Get the authenticated user.
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

        Long senderId = userPrincipal.getUserId();

        // Reuse the existing service to save the message.
        MessageResponse savedMessage = messageService.sendMessage(
                conversationId,
                senderId,
                request
        );

        // Broadcast the saved message to conversation subscribers.
        String destination =
                "/topic/conversations/" + conversationId + "/messages";

        messagingTemplate.convertAndSend(destination, savedMessage);
    }
}
