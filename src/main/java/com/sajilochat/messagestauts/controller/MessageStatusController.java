
package com.sajilochat.messagestauts.controller;

import com.sajilochat.messagestatus.dto.MessageStatusResponse;
import com.sajilochat.messagestatus.service.MessageStatusService;
import com.sajilochat.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MessageStatusController {

    private final MessageStatusService messageStatusService;

    // View delivery and read statuses for a message.
    // Only the original sender can access this information.
    @GetMapping("/messages/{messageId}/statuses")
    public ResponseEntity<List<MessageStatusResponse>> getMessageStatuses(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {

        List<MessageStatusResponse> statuses =
                messageStatusService.getMessageStatuses(
                        messageId,
                        principal.getUserId()
                );

        return ResponseEntity.ok(statuses);
    }

    // Mark a received message as delivered.
    @PatchMapping("/messages/{messageId}/delivered")
    public ResponseEntity<MessageStatusResponse> markAsDelivered(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {

        MessageStatusResponse response =
                messageStatusService.markAsDelivered(
                        messageId,
                        principal.getUserId()
                );

        return ResponseEntity.ok(response);
    }

    // Mark a received message as read.
    @PatchMapping("/messages/{messageId}/read")
    public ResponseEntity<MessageStatusResponse> markAsRead(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {

        MessageStatusResponse response =
                messageStatusService.markAsRead(
                        messageId,
                        principal.getUserId()
                );

        return ResponseEntity.ok(response);
    }
}
