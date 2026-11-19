
package com.sajilochat.message.repository;

import com.sajilochat.message.entity.MessageUserDeletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageUserDeletionRepository
        extends JpaRepository<MessageUserDeletion, Long> {

    boolean existsByMessageIdAndUserId(Long messageId, Long userId);

    List<MessageUserDeletion> findByUserId(Long userId);
}
