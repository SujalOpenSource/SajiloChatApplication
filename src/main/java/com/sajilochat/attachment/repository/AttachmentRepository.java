
package com.sajilochat.attachment.repository;

import com.sajilochat.attachment.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository
        extends JpaRepository<Attachment, Long> {

    List<Attachment> findAllByMessageIdOrderByCreatedAtAsc(
            Long messageId
    );

    Optional<Attachment> findByIdAndMessageId(
            Long attachmentId,
            Long messageId
    );
}
