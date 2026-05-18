package ch.exmachina.cosmo42.mappers;

import ch.exmachina.cosmo42.dto.JobStatusDTO;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class IngestionJobMapper {

    public IngestionJob toEntity(String originalFileName, long fileSizeBytes, String storedFileUuid) {
        IngestionJob job = new IngestionJob();
        job.setUuid(UUID.randomUUID().toString());
        job.setStatus(IngestionJobStatus.PENDING);
        job.setOriginalFileName(originalFileName);
        job.setFileSizeBytes(fileSizeBytes);
        job.setStoredFileUuid(storedFileUuid);
        job.setCreatedAt(LocalDateTime.now());
        return job;
    }

    public JobStatusDTO toStatusDTO(IngestionJob job, int progressPercent) {
        return JobStatusDTO.builder()
                .jobUuid(job.getUuid())
                .status(job.getStatus().name())
                .progressPercent(progressPercent)
                .documentUuid(job.getStatus() == IngestionJobStatus.COMPLETED
                        ? job.getKbDocumentUuid() : null)
                .errorMessage(job.getErrorMessage())
                .build();
    }
}
