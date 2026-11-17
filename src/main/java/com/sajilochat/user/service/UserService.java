
package com.sajilochat.user.service;

import com.sajilochat.block.repository.BlockRepository;
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
            throw new IllegalArgumentException(
                    "Search query must contain at least 2 characters"
            );
        }

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 50) {
            throw new IllegalArgumentException(
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
                                Sort.by(Sort.Direction.ASC, "username")
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

        User user = findUserById(userId);

        String bio = request.bio() != null
                ? request.bio().trim()
                : user.getBio();

        String profilePictureUrl = request.profilePictureUrl() != null
                ? request.profilePictureUrl().trim()
                : user.getProfilePictureUrl();

        user.updateProfile(
                request.firstName().trim(),
                request.lastName().trim(),
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

        if (!targetUserId.equals(currentUserId)
                && isBlockedBetween(currentUserId, targetUserId)) {

            // Do not reveal whether the target account exists
            // when a block relationship prevents profile access.
            throw new ResourceNotFoundException("User not found");
        }

        return toUserResponse(user);
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
                        new ResourceNotFoundException("User not found")
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
