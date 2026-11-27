
package com.sajilochat.attachment.service;

import com.sajilochat.attachment.dto.AttachmentResponse;
import com.sajilochat.attachment.entity.Attachment;
import com.sajilochat.attachment.repository.AttachmentRepository;
import com.sajilochat.block.repository.BlockRepository;
import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.conversation.entity.Conversation;
import com.sajilochat.conversation.entity.ConversationMember;
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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
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

        validateUploadedFile(file);

        Message message = getMessage(messageId);

        Conversation conversation = message.getConversation();

        verifyConversationMembership(
                conversation.getId(),
                userId
        );

        // Prevent access to messages from before a group rejoin.
        verifyMessageVisibleToCurrentMembership(message, userId);

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

        byte[] fileBytes;

        try {
            fileBytes = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unable to read the uploaded file",
                    exception
            );
        }

        if (fileBytes.length == 0 || fileBytes.length > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file size"
            );
        }

        // Never trust the MIME type supplied by the browser.
        validateActualFileContent(fileBytes, extension);

        Path storageRoot = getStorageRoot();

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

            // CREATE_NEW prevents accidental overwriting.
            Files.write(
                    destination,
                    fileBytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            Attachment attachment = Attachment.create(
                    message,
                    user,
                    originalFileName,
                    storedFileName,
                    expectedContentType,
                    (long) fileBytes.length,
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
    // FILE VALIDATION
    // =====================================================

    private void validateUploadedFile(MultipartFile file) {

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
    }

    private void validateActualFileContent(
            byte[] bytes,
            String extension
    ) {

        boolean valid = switch (extension) {
            case ".jpg", ".jpeg" -> isJpeg(bytes);
            case ".png" -> isPng(bytes);
            case ".gif" -> isGif(bytes);
            case ".webp" -> isWebp(bytes);
            case ".pdf" -> isPdf(bytes);
            case ".txt" -> isPlainText(bytes);
            case ".doc" -> isLegacyWordDocument(bytes);
            case ".docx" -> isDocxDocument(bytes);
            default -> false;
        };

        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The uploaded file content does not match its file extension"
            );
        }
    }

    private boolean isJpeg(byte[] bytes) {

        return bytes.length >= 3
                && unsigned(bytes[0]) == 0xFF
                && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {

        byte[] signature = {
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A
        };

        return startsWith(bytes, signature);
    }

    private boolean isGif(byte[] bytes) {

        return startsWith(
                bytes,
                "GIF87a".getBytes(StandardCharsets.US_ASCII)
        ) || startsWith(
                bytes,
                "GIF89a".getBytes(StandardCharsets.US_ASCII)
        );
    }

    private boolean isWebp(byte[] bytes) {

        return bytes.length >= 12
                && startsWith(
                bytes,
                "RIFF".getBytes(StandardCharsets.US_ASCII)
        )
                && matchesAscii(bytes, 8, "WEBP");
    }

    private boolean isPdf(byte[] bytes) {

        return startsWith(
                bytes,
                "%PDF-".getBytes(StandardCharsets.US_ASCII)
        );
    }

    private boolean isLegacyWordDocument(byte[] bytes) {

        byte[] oleSignature = {
                (byte) 0xD0,
                (byte) 0xCF,
                0x11,
                (byte) 0xE0,
                (byte) 0xA1,
                (byte) 0xB1,
                0x1A,
                (byte) 0xE1
        };

        return startsWith(bytes, oleSignature);
    }

    private boolean isDocxDocument(byte[] bytes) {

        // DOCX is a ZIP-based Office Open XML document.
        boolean zipSignature =
                bytes.length >= 4
                        && unsigned(bytes[0]) == 0x50
                        && unsigned(bytes[1]) == 0x4B
                        && (
                        unsigned(bytes[2]) == 0x03
                                || unsigned(bytes[2]) == 0x05
                                || unsigned(bytes[2]) == 0x07
                );

        if (!zipSignature) {
            return false;
        }

        // Verify that the archive contains the expected DOCX entries.
        String archiveContents = new String(
                bytes,
                StandardCharsets.ISO_8859_1
        );

        return archiveContents.contains("[Content_Types].xml")
                && archiveContents.contains("word/document.xml");
    }

    private boolean isPlainText(byte[] bytes) {

        // Text files should not contain null bytes.
        for (byte value : bytes) {
            if (value == 0) {
                return false;
            }
        }

        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));

            return true;

        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private boolean startsWith(byte[] bytes, byte[] signature) {

        if (bytes.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesAscii(
            byte[] bytes,
            int offset,
            String expected
    ) {

        byte[] expectedBytes =
                expected.getBytes(StandardCharsets.US_ASCII);

        if (bytes.length < offset + expectedBytes.length) {
            return false;
        }

        for (int i = 0; i < expectedBytes.length; i++) {
            if (bytes[offset + i] != expectedBytes[i]) {
                return false;
            }
        }

        return true;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
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

        // Do not expose attachments from before a group rejoin.
        verifyMessageVisibleToCurrentMembership(message, userId);

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

        Message message = attachment.getMessage();

        Conversation conversation = message.getConversation();

        verifyConversationMembership(
                conversation.getId(),
                userId
        );

        // Do not allow downloading attachments from before rejoining.
        verifyMessageVisibleToCurrentMembership(message, userId);

        ensureNoBlockRelationship(conversation, userId);

        if (message.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Message not found");
        }

        Path storageRoot = getStorageRoot();

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

        Message message = attachment.getMessage();

        Conversation conversation = message.getConversation();

        verifyConversationMembership(
                conversation.getId(),
                userId
        );

        // Apply the same group rejoin restriction to deletion.
        verifyMessageVisibleToCurrentMembership(message, userId);

        Path storageRoot = getStorageRoot();

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


    // =====================================================
    // GROUP REJOIN MESSAGE VISIBILITY
    // =====================================================

    private void verifyMessageVisibleToCurrentMembership(
            Message message,
            Long userId
    ) {

        Conversation originalConversation = message.getConversation();

        // The restriction applies only to group conversations.
        if (originalConversation.getType() != ConversationType.GROUP) {
            return;
        }

        // Load the current membership and its joinedAt timestamp.
        Conversation conversation = conversationRepository
                .findByIdAndMemberUserId(
                        originalConversation.getId(),
                        userId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException("Message not found")
                );

        Instant joinedAt = conversation.getMembers()
                .stream()
                .filter(member ->
                        member.getUser().getId().equals(userId)
                )
                .map(ConversationMember::getJoinedAt)
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException("Message not found")
                );

        // Messages created before the current joining time are hidden.
        if (message.getCreatedAt().isBefore(joinedAt)) {
            throw new ResourceNotFoundException("Message not found");
        }
    }


    // =====================================================
    // STORAGE HELPERS
    // =====================================================

    private Path getStorageRoot() {

        return Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize();
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


    // =====================================================
    // RESPONSE MAPPING
    // =====================================================

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
