
package com.sajilochat.typing.websocket;

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

        // Prevent clients from sending an event for another conversation.
        if (!conversationId.equals(request.conversationId())) {
            throw new IllegalArgumentException(
                    "Conversation ID does not match destination"
            );
        }

        TypingEvent event = new TypingEvent(
                conversationId,
                request.typing()
        );

        String destination =
                "/topic/conversations/" + conversationId + "/typing";


        // Broadcast typing information to conversation members.
        messagingTemplate.convertAndSend(
                destination,
                new TypingNotification(
                        userPrincipal.getUserId(),
                        userPrincipal.getUsername(),
                        conversationId,
                        request.typing()
                )
        );
    }

    public record TypingNotification(
            Long userId,
            String username,
            Long conversationId,
            Boolean typing
    ) {
    }
}
