
package com.sajilochat.security;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            String authorizationHeader =
                    accessor.getFirstNativeHeader("Authorization");

            if (!StringUtils.hasText(authorizationHeader)
                    || !authorizationHeader.startsWith("Bearer ")) {
                throw new MessageDeliveryException(
                        message,
                        "Missing or invalid Authorization header"
                );
            }

            String token = authorizationHeader.substring(7).trim();

            if (!StringUtils.hasText(token)
                    || token.contains(" ")
                    || token.contains("\"")
                    || token.contains("{")
                    || token.contains("}")) {
                throw new MessageDeliveryException(
                        message,
                        "Authorization header must contain only a JWT access token"
                );
            }

            try {
                String username = jwtService.extractUsername(token);

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(username);

                if (!userDetails.isEnabled()
                        || !jwtService.isTokenValid(token, userDetails)) {
                    throw new MessageDeliveryException(
                            message,
                            "Invalid or expired access token"
                    );
                }

                Authentication authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                accessor.setUser(authentication);

            } catch (JwtException | UsernameNotFoundException exception) {
                throw new MessageDeliveryException(
                        message,
                        "WebSocket authentication failed",
                        exception
                );
            }
        }

        return message;
    }
}
