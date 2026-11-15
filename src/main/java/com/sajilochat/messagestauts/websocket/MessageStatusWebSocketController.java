
package com.sajilochat.messagestatus.websocket;

import com.sajilochat.messagestatus.dto.MessageStatusResponse;
import com.sajilochat.messagestatus.service.MessageStatusService;
import com.sajilochat.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class MessageStatusWebSocketController {

    private final MessageStatusService messageStatusService;

    @MessageMapping("/messages/{messageId}/delivered")
    public void markAsDelivered(
            @DestinationVariable Long messageId,
            Principal principal
    ) {

        Long currentUserId = getCurrentUserId(principal);

        messageStatusService.markAsDelivered(
                messageId,
                currentUserId
        );
    }

    @MessageMapping("/messages/{messageId}/read")
    public void markAsRead(
            @DestinationVariable Long messageId,
            Principal principal
    ) {

        Long currentUserId = getCurrentUserId(principal);

        messageStatusService.markAsRead(
                messageId,
                currentUserId
        );
    }

    private Long getCurrentUserId(Principal principal) {

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

        return userPrincipal.getUserId();
    }
}
