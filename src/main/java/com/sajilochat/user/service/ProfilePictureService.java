
package com.sajilochat.user.service;

import com.sajilochat.block.repository.BlockRepository;
import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.enums.UserStatus;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfilePictureService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private static final String PROFILE_URL_PREFIX =
            "/api/v1/users/";

    private static final Pattern SAFE_FILENAME = Pattern.compile(
            "[0-9a-fA-F-]{36}\\.(jpg|png|webp)"
    );

    private final UserRepository userRepository;
    private final BlockRepository blockRepository;

    @Value("${app.profile-picture.storage-dir:./uploads/profile-pictures}")
    private String storageDirectory;

    // =====================================================
    // UPLOAD PROFILE PICTURE
    // =====================================================

    @Transactional
    public String uploadProfilePicture(
            Long userId,
            MultipartFile file
    ) {

        validateUploadedFile(file);

        byte[] bytes;

        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unable to read the uploaded image",
                    exception
            );
        }

        if (bytes.length == 0 || bytes.length > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image size must not exceed 5 MB"
            );
        }

        String extension = detectImageExtension(bytes);

        User user = getUser(userId);

        Path storageRoot = getStorageRoot();

        String storedFileName = UUID.randomUUID() + extension;

        Path destination = storageRoot.resolve(storedFileName)
                .normalize();

        if (!destination.startsWith(storageRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid image path"
            );
        }

        String previousUrl = user.getProfilePictureUrl();

        String newUrl = PROFILE_URL_PREFIX
                + userId
                + "/profile-picture/"
                + storedFileName;

        try {
            Files.createDirectories(storageRoot);

            Files.write(
                    destination,
                    bytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            try {
                user.updateProfilePictureUrl(newUrl);
                userRepository.saveAndFlush(user);

                registerUploadCleanup(destination, previousUrl, userId);

                return newUrl;

            } catch (RuntimeException exception) {
                deletePhysicalFile(destination);
                throw exception;
            }

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store the profile picture",
                    exception
            );
        }
    }

    // =====================================================
    // GET PROFILE PICTURE
    // =====================================================

    public ProfilePictureDownload getProfilePicture(
            Long targetUserId,
            String fileName,
            Long currentUserId
    ) {

        if (fileName == null
                || !SAFE_FILENAME.matcher(fileName).matches()) {
            throw new ResourceNotFoundException(
                    "Profile picture not found"
            );
        }

        User user = getUser(targetUserId);

        if (!targetUserId.equals(currentUserId)) {

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new ResourceNotFoundException("User not found");
            }

            boolean blockedByCurrentUser =
                    blockRepository.existsByBlockerIdAndBlockedId(
                            currentUserId,
                            targetUserId
                    );

            boolean blockedByTargetUser =
                    blockRepository.existsByBlockerIdAndBlockedId(
                            targetUserId,
                            currentUserId
                    );

            if (blockedByCurrentUser || blockedByTargetUser) {
                throw new ResourceNotFoundException("User not found");
            }
        }

        String expectedUrl = PROFILE_URL_PREFIX
                + targetUserId
                + "/profile-picture/"
                + fileName;

        if (!expectedUrl.equals(user.getProfilePictureUrl())) {
            throw new ResourceNotFoundException(
                    "Profile picture not found"
            );
        }

        Path storageRoot = getStorageRoot();

        Path filePath = storageRoot.resolve(fileName)
                .toAbsolutePath()
                .normalize();

        if (!filePath.startsWith(storageRoot)
                || !Files.isRegularFile(filePath)) {
            throw new ResourceNotFoundException(
                    "Profile picture not found"
            );
        }

        String contentType = getContentType(fileName);

        return new ProfilePictureDownload(
                new FileSystemResource(filePath),
                contentType
        );
    }

    // =====================================================
    // REMOVE PROFILE PICTURE
    // =====================================================

    @Transactional
    public void removeProfilePicture(Long userId) {

        User user = getUser(userId);

        String previousUrl = user.getProfilePictureUrl();

        if (previousUrl == null) {
            return;
        }

        user.updateProfilePictureUrl(null);
        userRepository.saveAndFlush(user);

        registerRemovalCleanup(previousUrl, userId);
    }

    // =====================================================
    // VALIDATION
    // =====================================================

    private void validateUploadedFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please select an image"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image size must not exceed 5 MB"
            );
        }
    }

    private String detectImageExtension(byte[] bytes) {

        if (isJpeg(bytes)) {
            return ".jpg";
        }

        if (isPng(bytes)) {
            return ".png";
        }

        if (isWebp(bytes)) {
            return ".webp";
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only JPEG, PNG, and WebP images are allowed"
        );
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

    private boolean isWebp(byte[] bytes) {

        return bytes.length >= 12
                && matchesAscii(bytes, 0, "RIFF")
                && matchesAscii(bytes, 8, "WEBP");
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

    private String getContentType(String fileName) {

        String lowerCaseName =
                fileName.toLowerCase(Locale.ROOT);

        if (lowerCaseName.endsWith(".jpg")) {
            return "image/jpeg";
        }

        if (lowerCaseName.endsWith(".png")) {
            return "image/png";
        }

        if (lowerCaseName.endsWith(".webp")) {
            return "image/webp";
        }

        return "application/octet-stream";
    }

    // =====================================================
    // TRANSACTION-SAFE FILE CLEANUP
    // =====================================================

    private void registerUploadCleanup(
            Path newFile,
            String previousUrl,
            Long userId
    ) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {

                        if (status == STATUS_COMMITTED) {
                            deleteStoredPicture(previousUrl, userId);
                        } else {
                            deletePhysicalFile(newFile);
                        }
                    }
                }
        );
    }

    private void registerRemovalCleanup(
            String previousUrl,
            Long userId
    ) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {

                        if (status == STATUS_COMMITTED) {
                            deleteStoredPicture(previousUrl, userId);
                        }
                    }
                }
        );
    }

    private void deleteStoredPicture(
            String pictureUrl,
            Long userId
    ) {

        String prefix = PROFILE_URL_PREFIX
                + userId
                + "/profile-picture/";

        if (pictureUrl == null || !pictureUrl.startsWith(prefix)) {
            return;
        }

        String fileName = pictureUrl.substring(prefix.length());

        if (!SAFE_FILENAME.matcher(fileName).matches()) {
            return;
        }

        Path storageRoot = getStorageRoot();

        Path filePath = storageRoot.resolve(fileName)
                .toAbsolutePath()
                .normalize();

        if (filePath.startsWith(storageRoot)) {
            deletePhysicalFile(filePath);
        }
    }

    private void deletePhysicalFile(Path path) {

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A cleanup failure must not hide the main operation result.
        }
    }

    // =====================================================
    // GENERAL HELPERS
    // =====================================================

    private Path getStorageRoot() {

        return Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize();
    }

    private User getUser(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );
    }

    public record ProfilePictureDownload(
            Resource resource,
            String contentType
    ) {
    }
}
