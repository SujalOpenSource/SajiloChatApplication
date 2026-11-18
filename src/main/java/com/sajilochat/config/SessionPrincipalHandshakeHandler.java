
package com.sajilochat.config;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Slf4j
@Component
public class SessionPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    public static final String AUTHENTICATION_ATTRIBUTE =
            "SAJILOCHAT_AUTHENTICATION";

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {

        // 1. Check the authentication available during the handshake.
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (isAuthenticated(authentication)) {
            return storeAuthentication(
                    authentication,
                    attributes,
                    "SecurityContextHolder"
            );
        }

        if (request instanceof ServletServerHttpRequest servletRequest) {

            // 2. Check the servlet request principal.
            Principal requestPrincipal =
                    servletRequest.getServletRequest().getUserPrincipal();

            if (requestPrincipal instanceof Authentication requestAuthentication
                    && isAuthenticated(requestAuthentication)) {

                return storeAuthentication(
                        requestAuthentication,
                        attributes,
                        "servlet request principal"
                );
            }

            // 3. Check the HTTP session SecurityContext.
            HttpSession session = servletRequest
                    .getServletRequest()
                    .getSession(false);

            if (session != null) {

                Object contextAttribute = session.getAttribute(
                        HttpSessionSecurityContextRepository
                                .SPRING_SECURITY_CONTEXT_KEY
                );

                if (contextAttribute instanceof SecurityContext context) {

                    Authentication sessionAuthentication =
                            context.getAuthentication();

                    if (isAuthenticated(sessionAuthentication)) {
                        return storeAuthentication(
                                sessionAuthentication,
                                attributes,
                                "HTTP session"
                        );
                    }
                }
            }
        }

        log.warn("No authenticated principal found during WebSocket handshake.");

        return super.determineUser(request, wsHandler, attributes);
    }

    private Authentication storeAuthentication(
            Authentication authentication,
            Map<String, Object> attributes,
            String source
    ) {

        // Preserve the server-verified authentication in WebSocket session attributes.
        attributes.put(AUTHENTICATION_ATTRIBUTE, authentication);

        log.debug(
                "WebSocket authenticated using {}: {}",
                source,
                authentication.getName()
        );

        return authentication;
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
