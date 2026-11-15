package com.sajilochat.presence.websocket;

import com.sajilochat.presence.service.PresenceService;
import com.sajilochat.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {

        Principal principal = event.getUser();

        UserPrincipal userPrincipal = extractUserPrincipal(principal);

        if (userPrincipal == null) {
            return;
        }

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();

        presenceService.userConnected(
                userPrincipal.getUserId(),
                sessionId
        );
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {

        presenceService.userDisconnected(event.getSessionId());
    }

    private UserPrincipal extractUserPrincipal(Principal principal) {

        if (!(principal instanceof Authentication authentication)) {
            return null;
        }

        Object authenticatedPrincipal = authentication.getPrincipal();

        if (!(authenticatedPrincipal instanceof UserPrincipal userPrincipal)) {
            return null;
        }

        return userPrincipal;
    }
}