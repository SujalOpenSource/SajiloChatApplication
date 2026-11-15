
package com.sajilochat.attachment.dto;

import java.time.Instant;

public record AttachmentResponse(
        Long id,
        Long messageId,
        Long conversationId,
        Long uploadedById,
        String uploadedByUsername,
        String originalFileName,
        String contentType,
        Long fileSize,
        String downloadUrl,
        Instant createdAt
) {}
