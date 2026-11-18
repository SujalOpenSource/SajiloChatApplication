
package com.sajilochat.user.service;

import com.sajilochat.block.repository.BlockRepository;
import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.user.dto.UpdateProfileRequest;
import com.sajilochat.user.dto.UserProfileResponse;
import com.sajilochat.user.dto.UserResponse;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.enums.UserStatus;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BlockRepository blockRepository;

    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(
            Long currentUserId,
            String query,
            int page,
            int size
    ) {

        if (query == null || query.trim().length() < 2) {
            throw new BadRequestException(
                    "Search query must contain at least 2 characters"
            );
        }

        if (page < 0) {
            throw new BadRequestException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 50) {
            throw new BadRequestException(
                    "Page size must be between 1 and 50"
            );
        }

        return userRepository.searchUsers(
                        query.trim(),
                        currentUserId,
                        UserStatus.ACTIVE,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                        Sort.Direction.ASC,
                                        "username"
                                )
                        )
                )
                .map(this::toUserResponse);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {

        User user = findUserById(userId);

        return toUserProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateMyProfile(
            Long userId,
            UpdateProfileRequest request
    ) {

        if (request.firstName() == null
                && request.lastName() == null
                && request.bio() == null
                && request.profilePictureUrl() == null) {

            throw new BadRequestException(
                    "At least one profile field must be provided"
            );
        }

        User user = findUserById(userId);

        String firstName = normalizeName(
                request.firstName(),
                user.getFirstName(),
                "First name"
        );

        String lastName = normalizeName(
                request.lastName(),
                user.getLastName(),
                "Last name"
        );

        String bio = request.bio() != null
                ? request.bio().trim()
                : user.getBio();

        String profilePictureUrl = normalizeProfilePictureUrl(
                request.profilePictureUrl(),
                user.getProfilePictureUrl()
        );

        user.updateProfile(
                firstName,
                lastName,
                bio,
                profilePictureUrl
        );

        User updatedUser = userRepository.save(user);

        return toUserProfileResponse(updatedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getPublicProfile(
            Long targetUserId,
            Long currentUserId
    ) {

        User user = findUserById(targetUserId);

        // Allow users to access their own profile.
        if (!targetUserId.equals(currentUserId)) {

            // Hide inactive and suspended accounts.
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new ResourceNotFoundException(
                        "User not found"
                );
            }

            // Hide profiles when either user has blocked the other.
            if (isBlockedBetween(currentUserId, targetUserId)) {
                throw new ResourceNotFoundException(
                        "User not found"
                );
            }
        }

        return toUserResponse(user);
    }

    private String normalizeName(
            String requestedName,
            String existingName,
            String fieldName
    ) {

        if (requestedName == null) {
            return existingName;
        }

        String normalizedName = requestedName.trim();

        if (normalizedName.isEmpty()) {
            throw new BadRequestException(
                    fieldName + " cannot be blank"
            );
        }

        return normalizedName;
    }

    private String normalizeProfilePictureUrl(
            String requestedUrl,
            String existingUrl
    ) {

        if (requestedUrl == null) {
            return existingUrl;
        }

        String normalizedUrl = requestedUrl.trim();

        // An empty value removes the profile picture.
        if (normalizedUrl.isEmpty()) {
            return null;
        }

        try {

            URI uri = URI.create(normalizedUrl);
            String scheme = uri.getScheme();

            boolean validScheme = scheme != null
                    && (scheme.equalsIgnoreCase("http")
                    || scheme.equalsIgnoreCase("https"));

            if (!uri.isAbsolute()
                    || !validScheme
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {

                throw new BadRequestException(
                        "Profile picture URL must be a valid HTTP or HTTPS URL"
                );
            }

            return normalizedUrl;

        } catch (IllegalArgumentException exception) {

            throw new BadRequestException(
                    "Profile picture URL is invalid"
            );
        }
    }

    private boolean isBlockedBetween(
            Long firstUserId,
            Long secondUserId
    ) {

        boolean firstBlockedSecond =
                blockRepository.existsByBlockerIdAndBlockedId(
                        firstUserId,
                        secondUserId
                );

        boolean secondBlockedFirst =
                blockRepository.existsByBlockerIdAndBlockedId(
                        secondUserId,
                        firstUserId
                );

        return firstBlockedSecond || secondBlockedFirst;
    }

    private User findUserById(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found"
                        )
                );
    }

    private UserResponse toUserResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getProfilePictureUrl(),
                user.getBio(),
                user.getLastSeenAt()
        );
    }

    private UserProfileResponse toUserProfileResponse(User user) {

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getProfilePictureUrl(),
                user.getBio(),
                user.getStatus(),
                user.getLastSeenAt(),
                user.getCreatedAt()
        );
    }
}
