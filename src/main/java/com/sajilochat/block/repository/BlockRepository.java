
package com.sajilochat.block.repository;

import com.sajilochat.block.entity.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(
            Long blockerId,
            Long blockedId
    );

    boolean existsByBlockerIdAndBlockedId(
            Long blockerId,
            Long blockedId
    );

    Page<UserBlock> findAllByBlockerIdOrderByCreatedAtDesc(
            Long blockerId,
            Pageable pageable
    );

    long deleteByBlockerIdAndBlockedId(
            Long blockerId,
            Long blockedId
    );
}
