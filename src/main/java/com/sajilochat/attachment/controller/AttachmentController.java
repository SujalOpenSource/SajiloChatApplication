
package com.sajilochat.attachment.controller;

import com.sajilochat.attachment.dto.AttachmentResponse;
import com.sajilochat.attachment.service.AttachmentService;
import com.sajilochat.attachment.service.AttachmentService.AttachmentDownload;
import com.sajilochat.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    // Upload an attachment to an existing message
    @PostMapping(
            value = "/api/v1/messages/{messageId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @PathVariable Long messageId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        UserPrincipal userPrincipal =
                (UserPrincipal) authentication.getPrincipal();

        AttachmentResponse response =
                attachmentService.uploadAttachment(
                        messageId,
                        userPrincipal.getUserId(),
                        file
                );

        return ResponseEntity.ok(response);
    }

    // Get all attachments belonging to a message
    @GetMapping("/api/v1/messages/{messageId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> getMessageAttachments(
            @PathVariable Long messageId,
            Authentication authentication
    ) {
        UserPrincipal userPrincipal =
                (UserPrincipal) authentication.getPrincipal();

        List<AttachmentResponse> attachments =
                attachmentService.getMessageAttachments(
                        messageId,
                        userPrincipal.getUserId()
                );

        return ResponseEntity.ok(attachments);
    }

    // Download an attachment
    @GetMapping("/api/v1/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long attachmentId,
            Authentication authentication
    ) {
        UserPrincipal userPrincipal =
                (UserPrincipal) authentication.getPrincipal();

        AttachmentDownload download =
                attachmentService.getAttachment(
                        attachmentId,
                        userPrincipal.getUserId()
                );

        ContentDisposition contentDisposition =
                ContentDisposition.attachment()
                        .filename(
                                download.originalFileName(),
                                StandardCharsets.UTF_8
                        )
                        .build();

        MediaType mediaType;

        try {
            mediaType = MediaType.parseMediaType(
                    download.contentType()
            );
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition.toString()
                )
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    // Delete an attachment
    @DeleteMapping("/api/v1/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long attachmentId,
            Authentication authentication
    ) {
        UserPrincipal userPrincipal =
                (UserPrincipal) authentication.getPrincipal();

        attachmentService.deleteAttachment(
                attachmentId,
                userPrincipal.getUserId()
        );

        return ResponseEntity.noContent().build();
    }
}
