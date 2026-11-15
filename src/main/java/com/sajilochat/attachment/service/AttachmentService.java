
package com.sajilochat.attachment.service;

import com.sajilochat.attachment.dto.AttachmentResponse;
import com.sajilochat.attachment.entity.Attachment;
import com.sajilochat.attachment.repository.AttachmentRepository;
import com.sajilochat.common.exception.ResourceNotFoundException;
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

        // Find the message
        Message message = getMessage(messageId);

        // Verify that the user belongs to the conversation
        verifyConversationMembership(
                message.getConversation().getId(),
                userId
        );

        // Only the message sender can attach files to that message
        if (!message.getSender().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only attach files to your own messages"
            );
        }

        User user = getUser(userId);

        // Extract and validate original filename
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

        // Validate the MIME type supplied by the client.
        // Some clients send application/octet-stream for valid files.
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

        // Prepare storage directory
        Path storageRoot = Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize();

        String storedFileName = UUID.randomUUID() + extension;

        Path destination = storageRoot.resolve(storedFileName)
                .normalize();

        // Prevent path traversal
        if (!destination.startsWith(storageRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file path"
            );
        }

        try {
            Files.createDirectories(storageRoot);

            // Store the file on the server
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

                // Remove the physical file if database persistence fails
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

        verifyConversationMembership(
                message.getConversation().getId(),
                userId
        );

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

        // Check conversation membership
        verifyConversationMembership(
                attachment.getMessage().getConversation().getId(),
                userId
        );

        // Do not allow downloading attachments of deleted messages
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

        // Only the uploader can delete the attachment
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
