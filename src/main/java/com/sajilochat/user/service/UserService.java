
package com.sajilochat.user.service;

import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.user.dto.UpdateProfileRequest;
import com.sajilochat.user.dto.UserProfileResponse;
import com.sajilochat.user.dto.UserResponse;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = findUserById(userId);

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

        return new UserProfileResponse(
                updatedUser.getId(),
                updatedUser.getUsername(),
                updatedUser.getFirstName(),
                updatedUser.getLastName(),
                updatedUser.getEmail(),
                updatedUser.getPhoneNumber(),
                updatedUser.getProfilePictureUrl(),
                updatedUser.getBio(),
                updatedUser.getStatus(),
                updatedUser.getLastSeenAt(),
                updatedUser.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getPublicProfile(Long userId) {
        User user = findUserById(userId);

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

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );
    }
}
