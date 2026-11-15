
package com.sajilochat.attachment.entity;

import com.sajilochat.message.entity.Message;
import com.sajilochat.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "attachments",
        indexes = {
                @Index(
                        name = "idx_attachments_message",
                        columnList = "message_id"
                ),
                @Index(
                        name = "idx_attachments_uploaded_by",
                        columnList = "uploaded_by"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, unique = true, length = 100)
    private String storedFileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 500)
    private String storagePath;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Attachment(
            Message message,
            User uploadedBy,
            String originalFileName,
            String storedFileName,
            String contentType,
            Long fileSize,
            String storagePath
    ) {
        this.message = message;
        this.uploadedBy = uploadedBy;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.storagePath = storagePath;
        this.createdAt = Instant.now();
    }

    public static Attachment create(
            Message message,
            User uploadedBy,
            String originalFileName,
            String storedFileName,
            String contentType,
            Long fileSize,
            String storagePath
    ) {
        return new Attachment(
                message,
                uploadedBy,
                originalFileName,
                storedFileName,
                contentType,
                fileSize,
                storagePath
        );
    }
}
