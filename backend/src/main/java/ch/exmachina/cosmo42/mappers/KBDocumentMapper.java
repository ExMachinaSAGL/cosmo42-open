package ch.exmachina.cosmo42.mappers;

import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.dto.DownloadDocumentDTO;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.entities.KBDocument;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class KBDocumentMapper {

    public DocumentDTO toDocumentDTO(IngestionJob job) {
        return DocumentDTO.builder()
                .fileUuid(job.getStoredFileUuid())
                .fileName(job.getOriginalFileName())
                .status(toFrontendStatus(job.getStatus()))
                .progressPercent(toProgressPercent(job))
                .uploadedAt(job.getCreatedAt())
                .errorMessage(job.getStatus() == IngestionJobStatus.FAILED ? job.getErrorMessage() : null)
                .build();
    }

    private static String toFrontendStatus(IngestionJobStatus status) {
        return switch (status) {
            case PENDING, PROCESSING, INTERRUPTED -> "loading";
            case COMPLETED -> "loaded";
            case FAILED -> "error";
        };
    }

    private static Integer toProgressPercent(IngestionJob job) {
        return switch (job.getStatus()) {
            case PENDING -> 0;
            case PROCESSING, INTERRUPTED -> {
                if (Boolean.TRUE.equals(job.getChunksEmbedded())) yield 95;
                if (job.getTotalPages() != null) yield 50;
                yield 10;
            }
            case COMPLETED -> 100;
            case FAILED -> null;
        };
    }

    public DownloadDocumentDTO toDownloadDocumentDTO(KBDocument kbDocument) {
        return DownloadDocumentDTO.builder()
                .fileUuid(kbDocument.getUuid())
                .fileName(kbDocument.getFileName())
                .uploadedAt(kbDocument.getCreationTimestamp())
                .build();
    }

}
