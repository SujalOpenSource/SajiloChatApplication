
package com.sajilochat.security;

import com.sajilochat.conversation.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class ConversationAuthorizationInterceptor implements ChannelInterceptor {

    private static final String SUBSCRIBE_DESTINATION =
            "/topic/conversations/";

    private static final String SEND_DESTINATION =
            "/app/conversations/";

    private static final String STATUS_SEND_DESTINATION =
            "/app/messages/";

    private static final String STATUS_SUBSCRIBE_DESTINATION =
            "/user/queue/message-status";

    private final ConversationRepository conversationRepository;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        }

        if (command == StompCommand.SEND) {
            authorizeSend(accessor);
        }

        return message;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {

        String destination = accessor.getDestination();

        if (destination == null) {
            throw new MessageDeliveryException(
                    "Subscription destination is required"
            );
        }

        // Private message-status queue.
        if (STATUS_SUBSCRIBE_DESTINATION.equals(destination)) {
            requireAuthenticatedUser(accessor.getUser());
            return;
        }

        // Initial presence snapshot subscription.
        if (destination.startsWith(SEND_DESTINATION)) {

            Long conversationId = extractConversationId(
                    destination,
                    SEND_DESTINATION,
                    "presence"
            );

            verifyMembership(accessor.getUser(), conversationId);
            return;
        }

        // Conversation topic subscriptions.
        if (destination.startsWith(SUBSCRIBE_DESTINATION)) {

            Long conversationId;

            if (destination.endsWith("/messages")) {

                conversationId = extractConversationId(
                        destination,
                        SUBSCRIBE_DESTINATION,
                        "messages"
                );

            } else if (destination.endsWith("/presence")) {

                conversationId = extractConversationId(
                        destination,
                        SUBSCRIBE_DESTINATION,
                        "presence"
                );

            } else if (destination.endsWith("/typing")) {

                conversationId = extractConversationId(
                        destination,
                        SUBSCRIBE_DESTINATION,
                        "typing"
                );

            } else if (destination.endsWith("/reactions")) {

                conversationId = extractConversationId(
                        destination,
                        SUBSCRIBE_DESTINATION,
                        "reactions"
                );

            } else {

                throw new MessageDeliveryException(
                        "Subscription destination is not allowed"
                );
            }

            verifyMembership(accessor.getUser(), conversationId);
            return;
        }

        throw new MessageDeliveryException(
                "Subscription destination is not allowed"
        );
    }

    private void authorizeSend(StompHeaderAccessor accessor) {

        String destination = accessor.getDestination();

        if (destination == null) {
            throw new MessageDeliveryException(
                    "Message destination is required"
            );
        }

        // Conversation messages and typing events.
        if (destination.startsWith(SEND_DESTINATION)) {

            Long conversationId;

            if (destination.endsWith("/messages")) {

                conversationId = extractConversationId(
                        destination,
                        SEND_DESTINATION,
                        "messages"
                );

            } else if (destination.endsWith("/typing")) {

                conversationId = extractConversationId(
                        destination,
                        SEND_DESTINATION,
                        "typing"
                );

            } else {

                throw new MessageDeliveryException(
                        "Message destination is not allowed"
                );
            }

            verifyMembership(accessor.getUser(), conversationId);
            return;
        }

        // Delivery and read receipt events.
        if (destination.startsWith(STATUS_SEND_DESTINATION)) {

            validateStatusDestination(destination);
            requireAuthenticatedUser(accessor.getUser());
            return;
        }

        throw new MessageDeliveryException(
                "Message destination is not allowed"
        );
    }

    private void validateStatusDestination(String destination) {

        String remainingPath = destination.substring(
                STATUS_SEND_DESTINATION.length()
        );

        String[] segments = remainingPath.split("/");

        if (segments.length != 2
                || !("delivered".equals(segments[1])
                || "read".equals(segments[1]))) {

            throw new MessageDeliveryException(
                    "Invalid message status destination"
            );
        }

        try {

            long messageId = Long.parseLong(segments[0]);

            if (messageId <= 0) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException exception) {

            throw new MessageDeliveryException(
                    null,
                    "Invalid message ID",
                    exception
            );
        }
    }

    private Long extractConversationId(
            String destination,
            String expectedPrefix,
            String expectedChannel
    ) {

        String remainingPath =
                destination.substring(expectedPrefix.length());

        String[] segments = remainingPath.split("/");

        if (segments.length != 2
                || !expectedChannel.equals(segments[1])) {

            throw new MessageDeliveryException(
                    "Invalid conversation destination"
            );
        }

        try {

            Long conversationId = Long.parseLong(segments[0]);

            if (conversationId <= 0) {
                throw new NumberFormatException();
            }

            return conversationId;

        } catch (NumberFormatException exception) {

            throw new MessageDeliveryException(
                    null,
                    "Invalid conversation ID",
                    exception
            );
        }
    }

    private UserPrincipal requireAuthenticatedUser(Principal principal) {

        if (!(principal instanceof Authentication authentication)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authenticated user not found"
            );
        }

        Object authenticatedPrincipal = authentication.getPrincipal();

        if (!(authenticatedPrincipal instanceof UserPrincipal userPrincipal)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authenticated user not found"
            );
        }

        return userPrincipal;
    }

    private void verifyMembership(
            Principal principal,
            Long conversationId
    ) {

        UserPrincipal userPrincipal = requireAuthenticatedUser(principal);

        boolean isMember = conversationRepository
                .findByIdAndMemberUserId(
                        conversationId,
                        userPrincipal.getUserId()
                )
                .isPresent();

        if (!isMember) {
            throw new MessageDeliveryException(
                    "You are not a member of this conversation"
            );
        }
    }
}
