
package com.sajilochat.message.controller;

import com.sajilochat.message.dto.EditMessageRequest;
import com.sajilochat.message.dto.MessageResponse;
import com.sajilochat.message.dto.SendMessageRequest;
import com.sajilochat.message.service.MessageService;
import com.sajilochat.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * Send a message to a conversation.
     */
    @PostMapping("/api/v1/conversations/{conversationId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SendMessageRequest request
    ) {
        MessageResponse response = messageService.sendMessage(
                conversationId,
                principal.getUserId(),
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieve all messages belonging to a conversation.
     */
    @GetMapping("/api/v1/conversations/{conversationId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<MessageResponse> messages = messageService.getMessages(
                conversationId,
                principal.getUserId()
        );

        return ResponseEntity.ok(messages);
    }

    /**
     * Edit a message sent by the authenticated user.
     */
    @PatchMapping("/api/v1/messages/{messageId}")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody EditMessageRequest request
    ) {
        MessageResponse response = messageService.editMessage(
                messageId,
                principal.getUserId(),
                request
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Soft-delete a message sent by the authenticated user.
     */
    @DeleteMapping("/api/v1/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        messageService.deleteMessage(
                messageId,
                principal.getUserId()
        );

        return ResponseEntity.noContent().build();
    }
}
