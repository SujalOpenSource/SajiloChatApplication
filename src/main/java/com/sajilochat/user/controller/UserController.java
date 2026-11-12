
package com.sajilochat.user.controller;

import com.sajilochat.security.UserPrincipal;
import com.sajilochat.user.dto.UpdateProfileRequest;
import com.sajilochat.user.dto.UserProfileResponse;
import com.sajilochat.user.dto.UserResponse;
import com.sajilochat.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UserProfileResponse response =
                userService.getMyProfile(principal.getUserId());

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UserProfileResponse response =
                userService.updateMyProfile(
                        principal.getUserId(),
                        request
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getPublicProfile(
            @PathVariable Long id
    ) {
        UserResponse response = userService.getPublicProfile(id);

        return ResponseEntity.ok(response);
    }
}
