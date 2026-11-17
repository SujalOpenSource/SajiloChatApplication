
package com.sajilochat.attachment.service;

import com.sajilochat.attachment.dto.AttachmentResponse;
import com.sajilochat.attachment.entity.Attachment;
import com.sajilochat.attachment.repository.AttachmentRepository;
import com.sajilochat.block.repository.BlockRepository;
import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.conversation.enums.ConversationType;
import com.sajilochat.conversation.repository.ConversationRepository;
import com.sajilochat.message.entity.Message;
import com.sajilochat.message.repository.MessageRepository;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".gif", "image/gif",
            ".webp", "image/webp",
            ".pdf", "application/pdf",
            ".txt", "text/plain",
            ".doc", "application/msword",
            ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final AttachmentRepository attachmentRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final BlockRepository blockRepository;

    @Value("${app.file.storage-dir:./uploads/attachments}")
    private String storageDirectory;

    // =====================================================
    // UPLOAD ATTACHMENT
    // =====================================================

    @Transactional
    public AttachmentResponse uploadAttachment(
            Long messageId,
            Long userId,
            MultipartFile file
    ) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please select a file"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File size must not exceed 10 MB"
            );
        }

        Message message = getMessage(messageId);

        Conversation conversation = message.getConversation();

        verifyConversationMembership(
                conversation.getId(),
                userId
        );

        ensureNoBlockRelationship(conversation, userId);

        if (!message.getSender().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only attach files to your own messages"
            );
        }

        User user = getUser(userId);

        String originalFileName = extractFileName(
                file.getOriginalFilename()
        );

        String extension = getExtension(originalFileName);

        String expectedContentType = ALLOWED_TYPES.get(extension);

        if (expectedContentType == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This file type is not allowed"
            );
        }

        String suppliedContentType = file.getContentType();

        if (suppliedContentType != null
                && !suppliedContentType.isBlank()
                && !suppliedContentType.equalsIgnoreCase("application/octet-stream")
                && !suppliedContentType.equalsIgnoreCase(expectedContentType)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File extension and content type do not match"
            );
        }

        Path storageRoot = Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize();

        String storedFileName = UUID.randomUUID() + extension;

        Path destination = storageRoot.resolve(storedFileName)
                .normalize();

        if (!destination.startsWith(storageRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file path"
            );
        }

        try {
            Files.createDirectories(storageRoot);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination);
            }

            Attachment attachment = Attachment.create(
                    message,
                    user,
                    originalFileName,
                    storedFileName,
                    expectedContentType,
                    file.getSize(),
                    destination.toString()
            );

            try {
                Attachment savedAttachment =
                        attachmentRepository.saveAndFlush(attachment);

                return mapToResponse(savedAttachment);

            } catch (RuntimeException exception) {
                deletePhysicalFile(destination);
                throw exception;
            }

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store the uploaded file",
                    exception
            );
        }
    }

    // =====================================================
    // GET MESSAGE ATTACHMENTS
    // =====================================================

    public List<AttachmentResponse> getMessageAttachments(
            Long messageId,
            Long userId
    ) {

        Message message = getMessage(messageId);

        Conversation conversation = message.getConversation();

        verifyConversationMembership(
                conversation.getId(),
                userId
        );

        ensureNoBlockRelationship(conversation, userId);

        return attachmentRepository
                .findAllByMessageIdOrderByCreatedAtAsc(messageId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =====================================================
    // DOWNLOAD ATTACHMENT
    // =====================================================

    public AttachmentDownload getAttachment(
            Long attachmentId,
            Long userId
    ) {

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Attachment not found")
                );

        Conversation conversation =
                attachment.getMessage().getConversation();

        verifyConversationMembership(
                conversation.getId(),
                userId
        );

        ensureNoBlockRelationship(conversation, userId);

        if (attachment.getMessage().getDeletedAt() != null) {
            throw new ResourceNotFoundException("Message not found");
        }

        Path storageRoot = Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize();

        Path filePath = Paths.get(attachment.getStoragePath())
                .toAbsolutePath()
                .normalize();

        if (!filePath.startsWith(storageRoot)
                || !Files.isRegularFile(filePath)) {

            throw new ResourceNotFoundException(
                    "Attachment file not found"
            );
        }

        Resource resource = new FileSystemResource(filePath);

        return new AttachmentDownload(
                resource,
                attachment.getOriginalFileName(),
                attachment.getContentType()
        );
    }

    // =====================================================
    // DELETE ATTACHMENT
    // =====================================================

    @Transactional
    public void deleteAttachment(
            Long attachmentId,
            Long userId
    ) {

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Attachment not found")
                );

        if (!attachment.getUploadedBy().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only delete your own attachments"
            );
        }

        Path storageRoot = Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize();

        Path filePath = Paths.get(attachment.getStoragePath())
                .toAbsolutePath()
                .normalize();

        if (!filePath.startsWith(storageRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid attachment storage path"
            );
        }

        try {
            Files.deleteIfExists(filePath);

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to delete attachment file",
                    exception
            );
        }

        attachmentRepository.delete(attachment);
    }

    // =====================================================
    // BLOCK ENFORCEMENT
    // =====================================================

    private void ensureNoBlockRelationship(
            Conversation conversation,
            Long userId
    ) {

        // Blocking does not restrict group attachments.
        if (conversation.getType() != ConversationType.PRIVATE) {
            return;
        }

        Long otherUserId = conversation.getMembers()
                .stream()
                .filter(member ->
                        !member.getUser().getId().equals(userId)
                )
                .map(member -> member.getUser().getId())
                .findFirst()
                .orElseThrow(() ->
                        new BadRequestException(
                                "Private conversation participant could not be found"
                        )
                );

        boolean blockedByCurrentUser =
                blockRepository.existsByBlockerIdAndBlockedId(
                        userId,
                        otherUserId
                );

        boolean blockedByOtherUser =
                blockRepository.existsByBlockerIdAndBlockedId(
                        otherUserId,
                        userId
                );

        if (blockedByCurrentUser || blockedByOtherUser) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Attachments are unavailable because one of you has blocked the other"
            );
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private Message getMessage(Long messageId) {

        return messageRepository.findByIdAndDeletedAtIsNull(messageId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Message not found")
                );
    }

    private User getUser(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );
    }

    private void verifyConversationMembership(
            Long conversationId,
            Long userId
    ) {

        boolean isMember = conversationRepository
                .findByIdAndMemberUserId(conversationId, userId)
                .isPresent();

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not a member of this conversation"
            );
        }
    }

    private String extractFileName(String originalFileName) {

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file name"
            );
        }

        String normalized = originalFileName.replace('\\', '/');

        String fileName = normalized.substring(
                normalized.lastIndexOf('/') + 1
        );

        if (fileName.isBlank()
                || fileName.length() > 255
                || fileName.contains("\u0000")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file name"
            );
        }

        return fileName;
    }

    private String getExtension(String fileName) {

        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private AttachmentResponse mapToResponse(Attachment attachment) {

        return new AttachmentResponse(
                attachment.getId(),
                attachment.getMessage().getId(),
                attachment.getMessage().getConversation().getId(),
                attachment.getUploadedBy().getId(),
                attachment.getUploadedBy().getUsername(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                "/api/v1/attachments/" + attachment.getId() + "/download",
                attachment.getCreatedAt()
        );
    }

    private void deletePhysicalFile(Path filePath) {

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Preserve the original database exception.
        }
    }

    // =====================================================
    // DOWNLOAD RESPONSE
    // =====================================================

    public record AttachmentDownload(
            Resource resource,
            String originalFileName,
            String contentType
    ) {
    }
}
