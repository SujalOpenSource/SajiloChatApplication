
package com.sajilochat.block.controller;

import com.sajilochat.block.dto.BlockStatusResponse;
import com.sajilochat.block.service.BlockService;
import com.sajilochat.security.UserPrincipal;
import com.sajilochat.user.dto.UserResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping("/{userId}/block")
    public ResponseEntity<Void> blockUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId
    ) {
        blockService.blockUser(
                principal.getUserId(),
                userId
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/block")
    public ResponseEntity<Void> unblockUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId
    ) {
        blockService.unblockUser(
                principal.getUserId(),
                userId
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/blocked")
    public ResponseEntity<Page<UserResponse>> getBlockedUsers(
            @AuthenticationPrincipal UserPrincipal principal,

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(50)
            int size
    ) {
        Page<UserResponse> response = blockService.getBlockedUsers(
                principal.getUserId(),
                PageRequest.of(page, size)
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/block-status")
    public ResponseEntity<BlockStatusResponse> getBlockStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId
    ) {
        BlockStatusResponse response = blockService.getBlockStatus(
                principal.getUserId(),
                userId
        );

        return ResponseEntity.ok(response);
    }
}
