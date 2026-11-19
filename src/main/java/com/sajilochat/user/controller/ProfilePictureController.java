
package com.sajilochat.user.controller;

import com.sajilochat.security.UserPrincipal;
import com.sajilochat.user.dto.ProfilePictureResponse;
import com.sajilochat.user.service.ProfilePictureService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ProfilePictureController {

    private final ProfilePictureService profilePictureService;

    // UPLOAD PROFILE PICTURE
    @PostMapping(
            value = "/me/profile-picture",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ProfilePictureResponse> uploadProfilePicture(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam("file") MultipartFile file
    ) {
        String profilePictureUrl =
                profilePictureService.uploadProfilePicture(
                        userPrincipal.getUserId(),
                        file
                );

        return ResponseEntity.ok(
                new ProfilePictureResponse(profilePictureUrl)
        );
    }

    // GET PROFILE PICTURE
    @GetMapping("/{userId}/profile-picture/{fileName}")
    public ResponseEntity<Resource> getProfilePicture(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long userId,
            @PathVariable String fileName
    ) {
        var picture = profilePictureService.getProfilePicture(
                userId,
                fileName,
                userPrincipal.getUserId()
        );

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(picture.contentType())
                )
                .cacheControl(
                        CacheControl.maxAge(5, TimeUnit.MINUTES)
                                .cachePrivate()
                )
                .header(
                        "X-Content-Type-Options",
                        "nosniff"
                )
                .body(picture.resource());
    }

    // REMOVE PROFILE PICTURE
    @DeleteMapping("/me/profile-picture")
    public ResponseEntity<Void> removeProfilePicture(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        profilePictureService.removeProfilePicture(
                userPrincipal.getUserId()
        );

        return ResponseEntity.noContent().build();
    }
}
