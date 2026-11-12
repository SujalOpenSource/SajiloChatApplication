
package com.sajilochat.conversation.controller;

import com.sajilochat.conversation.dto.ConversationResponse;
import com.sajilochat.conversation.dto.CreateConversationRequest;
import com.sajilochat.conversation.service.ConversationService;
import com.sajilochat.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/private")
    public ResponseEntity<ConversationResponse> createPrivateConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateConversationRequest request
    ) {
        ConversationResponse response =
                conversationService.createPrivateConversation(
                        principal.getUserId(),
                        request.targetUserId()
                );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getMyConversations(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ConversationResponse> response =
                conversationService.getMyConversations(
                        principal.getUserId()
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> getConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long conversationId
    ) {
        ConversationResponse response =
                conversationService.getConversation(
                        conversationId,
                        principal.getUserId()
                );

        return ResponseEntity.ok(response);
    }
}
