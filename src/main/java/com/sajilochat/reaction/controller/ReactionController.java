
package com.sajilochat.reaction.controller;

import com.sajilochat.reaction.dto.AddReactionRequest;
import com.sajilochat.reaction.dto.ReactionResponse;
import com.sajilochat.reaction.service.ReactionService;
import com.sajilochat.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/messages")
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<ReactionResponse> addOrUpdateReaction(
            @PathVariable Long messageId,
            @Valid @RequestBody AddReactionRequest request,
            Authentication authentication
    ) {

        UserPrincipal userPrincipal =
                (UserPrincipal) authentication.getPrincipal();

        ReactionResponse response =
                reactionService.addOrUpdateReaction(
                        messageId,
                        userPrincipal.getUserId(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping("/{messageId}/reactions")
    public ResponseEntity<List<ReactionResponse>> getMessageReactions(
            @PathVariable Long messageId,
            Authentication authentication
    ) {

        UserPrincipal userPrincipal =
                (UserPrincipal) authentication.getPrincipal();

        return ResponseEntity.ok(
                reactionService.getMessageReactions(
                        messageId,
                        userPrincipal.getUserId()
                )
        );
    }

    @DeleteMapping("/{messageId}/reactions")
    public ResponseEntity<Void> removeReaction(
            @PathVariable Long messageId,
            Authentication authentication
    ) {

        UserPrincipal userPrincipal =
                (UserPrincipal) authentication.getPrincipal();

        reactionService.removeReaction(
                messageId,
                userPrincipal.getUserId()
        );

        return ResponseEntity.noContent().build();
    }
}
