package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.dto.JobStatusDTO;
import ch.exmachina.cosmo42.entities.*;
import ch.exmachina.cosmo42.repositories.IngestionJobPageRepository;
import ch.exmachina.cosmo42.repositories.IngestionJobRepository;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class IngestionJobService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;

    IngestionJobRepository ingestionJobRepository;
    IngestionJobPageRepository ingestionJobPageRepository;
    ObjectMapper objectMapper;

    @Transactional
    public IngestionJob createJob(String originalFileName, long fileSizeBytes, String storedFileUuid) {
        IngestionJob job = new IngestionJob();
        job.setUuid(UUID.randomUUID().toString());
        job.setStatus(IngestionJobStatus.PENDING);
        job.setOriginalFileName(originalFileName);
        job.setFileSizeBytes(fileSizeBytes);
        job.setStoredFileUuid(storedFileUuid);
        job.setCreatedAt(LocalDateTime.now());
        return ingestionJobRepository.save(job);
    }

    @Transactional
    public void markProcessing(String jobUuid) {
        IngestionJob job = loadOrThrow(jobUuid);
        job.setStatus(IngestionJobStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        ingestionJobRepository.save(job);
    }

    @Transactional
    public void markInterrupted(String jobUuid) {
        IngestionJob job = loadOrThrow(jobUuid);
        job.setStatus(IngestionJobStatus.INTERRUPTED);
        ingestionJobRepository.save(job);
    }

    @Transactional
    public void markCompleted(String jobUuid) {
        IngestionJob job = loadOrThrow(jobUuid);
        job.setStatus(IngestionJobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        ingestionJobRepository.save(job);
    }

    @Transactional
    public void markFailed(String jobUuid, String errorMessage) {
        String truncated = errorMessage != null
                ? errorMessage.substring(0, Math.min(errorMessage.length(), ERROR_MESSAGE_MAX_LENGTH))
                : null;
        IngestionJob job = loadOrThrow(jobUuid);
        job.setStatus(IngestionJobStatus.FAILED);
        job.setErrorMessage(truncated);
        ingestionJobRepository.save(job);
    }

    @Transactional
    public void markChunksEmbedded(String jobUuid) {
        IngestionJob job = loadOrThrow(jobUuid);
        job.setChunksEmbedded(true);
        ingestionJobRepository.save(job);
    }

    @Transactional
    public void setTotalPages(String jobUuid, int totalPages) {
        IngestionJob job = loadOrThrow(jobUuid);
        job.setTotalPages(totalPages);
        ingestionJobRepository.save(job);
        for (int i = 0; i < totalPages; i++) {
            if (!ingestionJobPageRepository.existsByJobAndPageIndex(job, i)) {
                IngestionJobPage page = new IngestionJobPage();
                page.setJob(job);
                page.setPageIndex(i);
                page.setStatus(IngestionJobPageStatus.PENDING);
                page.setAttemptCount(0);
                ingestionJobPageRepository.save(page);
            }
        }
    }

    @Transactional
    public void setKbDocumentUuid(String jobUuid, String kbDocumentUuid) {
        IngestionJob job = loadOrThrow(jobUuid);
        job.setKbDocumentUuid(kbDocumentUuid);
        ingestionJobRepository.save(job);
    }

    private IngestionJob loadOrThrow(String jobUuid) {
        return ingestionJobRepository.findByUuid(jobUuid).orElseThrow();
    }

    @Transactional
    public void savePageResult(IngestionJob job, int pageIndex, DocumentPage page) {
        IngestionJobPage entity = ingestionJobPageRepository.findByJobAndPageIndex(job, pageIndex).orElse(null);
        if (entity == null) {
            log.warn("Page {} not found for job {} while saving result", pageIndex, job.getUuid());
            return;
        }
        entity.setAttemptCount(entity.getAttemptCount() + 1);
        if (page == null) {
            entity.setStatus(IngestionJobPageStatus.FAILED);
            ingestionJobPageRepository.save(entity);
            return;
        }
        try {
            entity.setChunksJson(objectMapper.writeValueAsString(page));
            entity.setStatus(IngestionJobPageStatus.COMPLETED);
        } catch (JacksonException e) {
            log.error("Failed to serialize page {} result", pageIndex, e);
            entity.setStatus(IngestionJobPageStatus.FAILED);
        }
        ingestionJobPageRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<DocumentPage> loadCompletedPages(IngestionJob job) {
        return ingestionJobPageRepository
                .findByJobOrderByPageIndexAsc(job).stream()
                .filter(p -> p.getStatus() == IngestionJobPageStatus.COMPLETED)
                .map(this::deserializePage)
                .filter(Objects::nonNull)
                .toList();
    }

    private DocumentPage deserializePage(IngestionJobPage page) {
        try {
            return objectMapper.readValue(page.getChunksJson(), DocumentPage.class);
        } catch (JacksonException e) {
            log.error("Failed to deserialize page {} result", page.getPageIndex(), e);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Set<Integer> findRetryablePageIndices(IngestionJob job, int maxAttempts) {
        return new LinkedHashSet<>(ingestionJobPageRepository.findRetryablePageIndices(job, maxAttempts));
    }

    @Transactional(readOnly = true)
    public long countExhaustedFailures(IngestionJob job, int maxAttempts) {
        return ingestionJobPageRepository.countExhaustedFailures(job, maxAttempts);
    }

    @Transactional(readOnly = true)
    public Optional<IngestionJob> findByUuid(String uuid) {
        return ingestionJobRepository.findByUuid(uuid);
    }

    @Transactional(readOnly = true)
    public List<IngestionJob> findByStatuses(List<IngestionJobStatus> statuses) {
        return ingestionJobRepository.findByStatusIn(statuses);
    }

    @Transactional(readOnly = true)
    public List<JobStatusDTO> listJobs(List<IngestionJobStatus> statuses) {
        List<IngestionJob> jobs = (statuses == null || statuses.isEmpty())
                ? ingestionJobRepository.findAll()
                : ingestionJobRepository.findByStatusIn(statuses);
        return jobs.stream().map(this::toStatusDTO).toList();
    }

    public JobStatusDTO toStatusDTO(IngestionJob job) {
        return JobStatusDTO.builder()
                .jobUuid(job.getUuid())
                .status(job.getStatus().name())
                .progressPercent(calculateProgress(job))
                .documentUuid(job.getStatus() == IngestionJobStatus.COMPLETED
                        ? job.getKbDocumentUuid() : null)
                .errorMessage(job.getErrorMessage())
                .build();
    }

    private int calculateProgress(IngestionJob job) {
        if (job.getStatus() == IngestionJobStatus.COMPLETED) return 100;
        if (job.getStatus() == IngestionJobStatus.PENDING) return 0;

        if (job.getTotalPages() == null) return 0;

        int progress = 10; // PDF + images conversion done
        long completedPages = ingestionJobPageRepository.countByJobAndStatus(
                job, IngestionJobPageStatus.COMPLETED);
        progress += (int) (completedPages * 60L / job.getTotalPages());

        if (job.getStoredFileUuid() != null) progress += 10;
        if (job.getKbDocumentUuid() != null) progress += 10;

        return Math.min(progress, 99);
    }
}
