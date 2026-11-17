
package com.sajilochat.security;

import com.sajilochat.block.repository.BlockRepository;
import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.conversation.enums.ConversationType;
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
public class ConversationAuthorizationInterceptor
        implements ChannelInterceptor {

    private static final String SUBSCRIBE_DESTINATION =
            "/topic/conversations/";

    private static final String SEND_DESTINATION =
            "/app/conversations/";

    private static final String STATUS_SEND_DESTINATION =
            "/app/messages/";

    private static final String STATUS_SUBSCRIBE_DESTINATION =
            "/user/queue/message-status";

    private static final String NOTIFICATION_SUBSCRIBE_DESTINATION =
            "/user/queue/notifications";

    private static final String GROUP_EVENT_SUBSCRIBE_DESTINATION =
            "/user/queue/group-events";

    private final ConversationRepository conversationRepository;
    private final BlockRepository blockRepository;

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

        if (destination == null || destination.isBlank()) {
            throw new MessageDeliveryException(
                    "Subscription destination is required"
            );
        }

        // Private user queues.
        if (STATUS_SUBSCRIBE_DESTINATION.equals(destination)
                || NOTIFICATION_SUBSCRIBE_DESTINATION.equals(destination)
                || GROUP_EVENT_SUBSCRIBE_DESTINATION.equals(destination)) {

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

            String channel;

            if (destination.endsWith("/messages")) {
                channel = "messages";

            } else if (destination.endsWith("/presence")) {
                channel = "presence";

            } else if (destination.endsWith("/typing")) {
                channel = "typing";

            } else if (destination.endsWith("/reactions")) {
                channel = "reactions";

            } else {
                throw new MessageDeliveryException(
                        "Subscription destination is not allowed"
                );
            }

            Long conversationId = extractConversationId(
                    destination,
                    SUBSCRIBE_DESTINATION,
                    channel
            );

            verifyMembership(accessor.getUser(), conversationId);
            return;
        }

        throw new MessageDeliveryException(
                "Subscription destination is not allowed"
        );
    }

    private void authorizeSend(StompHeaderAccessor accessor) {

        String destination = accessor.getDestination();

        if (destination == null || destination.isBlank()) {
            throw new MessageDeliveryException(
                    "Message destination is required"
            );
        }

        // Conversation messages and typing events.
        if (destination.startsWith(SEND_DESTINATION)) {

            String channel;

            if (destination.endsWith("/messages")) {
                channel = "messages";

            } else if (destination.endsWith("/typing")) {
                channel = "typing";

            } else {
                throw new MessageDeliveryException(
                        "Message destination is not allowed"
                );
            }

            Long conversationId = extractConversationId(
                    destination,
                    SEND_DESTINATION,
                    channel
            );

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

        String[] segments = remainingPath.split("/", -1);

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

        String[] segments = remainingPath.split("/", -1);

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

        if (!(principal instanceof Authentication authentication)
                || !authentication.isAuthenticated()) {

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

        UserPrincipal userPrincipal =
                requireAuthenticatedUser(principal);

        Conversation conversation = conversationRepository
                .findByIdAndMemberUserId(
                        conversationId,
                        userPrincipal.getUserId()
                )
                .orElseThrow(() ->
                        new MessageDeliveryException(
                                "You are not a member of this conversation"
                        )
                );

        // Enforce blocking only for private conversations.
        // Group conversations remain accessible.
        if (conversation.getType() == ConversationType.PRIVATE) {

            Long currentUserId = userPrincipal.getUserId();

            Long otherUserId = conversation.getMembers()
                    .stream()
                    .map(member -> member.getUser().getId())
                    .filter(memberId -> !memberId.equals(currentUserId))
                    .findFirst()
                    .orElseThrow(() ->
                            new MessageDeliveryException(
                                    "Private conversation participant could not be found"
                            )
                    );

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
                throw new MessageDeliveryException(
                        "Private conversation access is unavailable because one of you has blocked the other"
                );
            }
        }
    }
}
