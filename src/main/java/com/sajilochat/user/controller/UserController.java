
package com.sajilochat.user.controller;

import org.springframework.data.domain.Page;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

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


@Validated
public class UserController {

    @GetMapping("/search")
    public ResponseEntity<Page<UserResponse>> searchUsers(
            @AuthenticationPrincipal UserPrincipal principal,

            @RequestParam
            @NotBlank
            @Size(min = 2, max = 50)
            String q,

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(50)
            int size
    ) {
        Page<UserResponse> response = userService.searchUsers(
                principal.getUserId(),
                q,
                page,
                size
        );

        return ResponseEntity.ok(response);
    }

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
