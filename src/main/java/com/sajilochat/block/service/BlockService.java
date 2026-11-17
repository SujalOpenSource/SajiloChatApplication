
package com.sajilochat.block.service;

import com.sajilochat.block.dto.BlockStatusResponse;
import com.sajilochat.block.entity.UserBlock;
import com.sajilochat.block.repository.BlockRepository;
import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.common.exception.ResourceNotFoundException;
import com.sajilochat.user.dto.UserResponse;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    @Transactional
    public void blockUser(Long blockerId, Long blockedId) {

        if (blockerId.equals(blockedId)) {
            throw new BadRequestException(
                    "You cannot block yourself"
            );
        }

        User blocker = findUserById(blockerId);
        User blocked = findUserById(blockedId);

        if (blockRepository.existsByBlockerIdAndBlockedId(
                blockerId, blockedId
        )) {
            throw new BadRequestException(
                    "You have already blocked this user"
            );
        }

        UserBlock userBlock = UserBlock.create(blocker, blocked);

        blockRepository.save(userBlock);
    }

    @Transactional
    public void unblockUser(Long blockerId, Long blockedId) {

        long deleted = blockRepository.deleteByBlockerIdAndBlockedId(
                blockerId,
                blockedId
        );

        if (deleted == 0) {
            throw new ResourceNotFoundException(
                    "Block relationship not found"
            );
        }
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getBlockedUsers(
            Long blockerId,
            Pageable pageable
    ) {
        return blockRepository
                .findAllByBlockerIdOrderByCreatedAtDesc(
                        blockerId,
                        pageable
                )
                .map(userBlock -> toUserResponse(
                        userBlock.getBlocked()
                ));
    }

    @Transactional(readOnly = true)
    public BlockStatusResponse getBlockStatus(
            Long currentUserId,
            Long targetUserId
    ) {
        if (currentUserId.equals(targetUserId)) {
            throw new BadRequestException(
                    "You cannot check block status against yourself"
            );
        }

        findUserById(targetUserId);

        boolean blockedByMe =
                blockRepository.existsByBlockerIdAndBlockedId(
                        currentUserId,
                        targetUserId
                );

        boolean blockedMe =
                blockRepository.existsByBlockerIdAndBlockedId(
                        targetUserId,
                        currentUserId
                );

        return new BlockStatusResponse(
                blockedByMe,
                blockedMe
        );
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
}
