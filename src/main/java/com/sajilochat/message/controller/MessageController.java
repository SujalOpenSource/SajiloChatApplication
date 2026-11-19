
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
     * Delete for everyone.
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

    /**
     * Delete only for the authenticated user.
     */
    @DeleteMapping("/api/v1/messages/{messageId}/me")
    public ResponseEntity<Void> deleteMessageForMe(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        messageService.deleteMessageForMe(
                messageId,
                principal.getUserId()
        );

        return ResponseEntity.noContent().build();
    }
}
