package ch.exmachina.cosmo42.mappers;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobPage;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionJobMapper {

    private final ObjectMapper objectMapper;

    public String toChunksJson(DocumentPage page) {
        try {
            return objectMapper.writeValueAsString(page);
        } catch (JacksonException e) {
            log.error("Failed to serialize page result", e);
            return null;
        }
    }

    public DocumentPage toDocumentPage(IngestionJobPage page) {
        try {
            return objectMapper.readValue(page.getChunksJson(), DocumentPage.class);
        } catch (JacksonException e) {
            log.error("Failed to deserialize page {} result", page.getPageIndex(), e);
            return null;
        }
    }

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
}
