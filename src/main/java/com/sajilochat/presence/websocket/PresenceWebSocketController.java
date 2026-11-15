
package com.sajilochat.presence.websocket;

import com.sajilochat.presence.dto.PresenceSnapshotResponse;
import com.sajilochat.presence.service.PresenceService;
import com.sajilochat.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class PresenceWebSocketController {

    private final PresenceService presenceService;

    @SubscribeMapping("/conversations/{conversationId}/presence")
    public PresenceSnapshotResponse getInitialPresence(
            @DestinationVariable Long conversationId,
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

        return presenceService.getPresenceSnapshot(
                conversationId,
                userPrincipal.getUserId()
        );
    }
}
