package ch.exmachina.cosmo42.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "ingestion_job")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class IngestionJob extends BaseEntity {

    @Column(nullable = false, unique = true)
    String uuid;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    IngestionJobStatus status;

    @Column(nullable = false)
    String originalFileName;

    @Column(nullable = false)
    Long fileSizeBytes;

    @Column
    String storedFileUuid;

    @Column
    String kbDocumentUuid;

    @Column
    Integer totalPages;

    @Column(nullable = false)
    LocalDateTime createdAt;

    @Column
    LocalDateTime startedAt;

    @Column
    LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    String errorMessage;
}
