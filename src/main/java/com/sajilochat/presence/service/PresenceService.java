
package com.sajilochat.presence.service;

import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.conversation.entity.ConversationMember;
import com.sajilochat.conversation.repository.ConversationRepository;
import com.sajilochat.presence.dto.PresenceEvent;
import com.sajilochat.presence.dto.PresenceSnapshotResponse;
import com.sajilochat.presence.dto.PresenceUserResponse;
import com.sajilochat.presence.enums.PresenceStatus;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String PRESENCE_DESTINATION =
            "/topic/conversations/";

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Tracks all active WebSocket sessions for each user.
    private final Map<Long, Set<String>> sessionsByUser = new HashMap<>();

    // Maps each WebSocket session to its user.
    private final Map<String, Long> userBySession = new HashMap<>();

    public synchronized void userConnected(Long userId, String sessionId) {

        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);

        if (user == null || userBySession.containsKey(sessionId)) {
            return;
        }

        Set<String> sessions = sessionsByUser.computeIfAbsent(
                userId,
                ignored -> new HashSet<>()
        );

        boolean wasOffline = sessions.isEmpty();

        sessions.add(sessionId);
        userBySession.put(sessionId, userId);

        if (wasOffline) {
            publishPresence(user, PresenceStatus.ONLINE, null);
        }
    }

    public synchronized void userDisconnected(String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        Long userId = userBySession.remove(sessionId);

        if (userId == null) {
            return;
        }

        Set<String> sessions = sessionsByUser.get(userId);

        if (sessions == null) {
            return;
        }

        sessions.remove(sessionId);

        // Another session is still connected.
        if (!sessions.isEmpty()) {
            return;
        }

        sessionsByUser.remove(userId);

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return;
        }

        Instant lastSeenAt = Instant.now();

        user.updateLastSeen(lastSeenAt);
        userRepository.save(user);

        publishPresence(
                user,
                PresenceStatus.OFFLINE,
                lastSeenAt
        );
    }

    // Returns the current online members of a conversation.
    @Transactional(readOnly = true)
    public PresenceSnapshotResponse getPresenceSnapshot(
            Long conversationId,
            Long requesterId
    ) {

        Conversation conversation = conversationRepository
                .findByIdAndMemberUserId(conversationId, requesterId)
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "You are not a member of this conversation"
                        )
                );

        List<PresenceUserResponse> onlineUsers =
                conversation.getMembers()
                        .stream()
                        .map(ConversationMember::getUser)
                        .filter(user -> isOnline(user.getId()))
                        .map(user -> new PresenceUserResponse(
                                user.getId(),
                                user.getUsername(),
                                PresenceStatus.ONLINE
                        ))
                        .toList();

        return new PresenceSnapshotResponse(
                conversationId,
                onlineUsers,
                Instant.now()
        );
    }

    // Checks whether a user has at least one active session.
    private synchronized boolean isOnline(Long userId) {

        Set<String> sessions = sessionsByUser.get(userId);

        return sessions != null && !sessions.isEmpty();
    }

    private void publishPresence(
            User user,
            PresenceStatus status,
            Instant lastSeenAt
    ) {

        PresenceEvent event = new PresenceEvent(
                user.getId(),
                user.getUsername(),
                status,
                lastSeenAt
        );

        for (Conversation conversation :
                conversationRepository.findAllByMemberUserId(user.getId())) {

            String destination = PRESENCE_DESTINATION
                    + conversation.getId()
                    + "/presence";

            messagingTemplate.convertAndSend(destination, event);
        }
    }
}
