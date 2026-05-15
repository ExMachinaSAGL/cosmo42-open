package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.dto.JobStatusDTO;
import ch.exmachina.cosmo42.entities.*;
import ch.exmachina.cosmo42.repositories.IngestionJobPageRepository;
import ch.exmachina.cosmo42.repositories.IngestionJobRepository;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        ingestionJobRepository.updateStatusAndStartedAt(jobUuid, IngestionJobStatus.PROCESSING, LocalDateTime.now());
    }

    @Transactional
    public void markInterrupted(String jobUuid) {
        ingestionJobRepository.updateStatus(jobUuid, IngestionJobStatus.INTERRUPTED);
    }

    @Transactional
    public void markCompleted(String jobUuid) {
        ingestionJobRepository.updateStatusAndCompletedAt(jobUuid, IngestionJobStatus.COMPLETED, LocalDateTime.now());
    }

    @Transactional
    public void markFailed(String jobUuid, String errorMessage) {
        String truncated = errorMessage != null
                ? errorMessage.substring(0, Math.min(errorMessage.length(), 2000))
                : null;
        ingestionJobRepository.updateStatusAndError(jobUuid, IngestionJobStatus.FAILED, truncated);
    }

    @Transactional
    public void setStoredFileUuid(String jobUuid, String storedFileUuid) {
        ingestionJobRepository.findByUuid(jobUuid).ifPresent(job ->
                job.setStoredFileUuid(storedFileUuid));
    }

    @Transactional
    public void setTotalPages(String jobUuid, int totalPages) {
        ingestionJobRepository.updateTotalPages(jobUuid, totalPages);
        IngestionJob job = ingestionJobRepository.findByUuid(jobUuid).orElseThrow();
        for (int i = 0; i < totalPages; i++) {
            if (!ingestionJobPageRepository.existsByJobAndPageIndex(job, i)) {
                IngestionJobPage page = new IngestionJobPage();
                page.setJob(job);
                page.setPageIndex(i);
                page.setStatus(IngestionJobPageStatus.PENDING);
                ingestionJobPageRepository.save(page);
            }
        }
    }

    @Transactional
    public void setKbDocumentUuid(String jobUuid, String kbDocumentUuid) {
        ingestionJobRepository.updateKbDocumentUuid(jobUuid, kbDocumentUuid);
    }

    @Transactional
    public void savePageResult(IngestionJob job, int pageIndex, DocumentPage page) {
        ingestionJobPageRepository.findByJobOrderByPageIndexAsc(job).stream()
                .filter(p -> p.getPageIndex() == pageIndex)
                .findFirst()
                .ifPresent(p -> {
                    if (page == null) {
                        p.setStatus(IngestionJobPageStatus.FAILED);
                    } else {
                        try {
                            p.setChunksJson(objectMapper.writeValueAsString(page));
                            p.setStatus(IngestionJobPageStatus.COMPLETED);
                        } catch (JsonProcessingException e) {
                            log.error("Failed to serialize page {} result", pageIndex, e);
                            p.setStatus(IngestionJobPageStatus.FAILED);
                        }
                    }
                });
    }

    @Transactional(readOnly = true)
    public List<DocumentPage> loadCompletedPages(IngestionJob job) {
        return ingestionJobPageRepository
                .findByJobOrderByPageIndexAsc(job).stream()
                .filter(p -> p.getStatus() == IngestionJobPageStatus.COMPLETED)
                .map(p -> {
                    try {
                        return objectMapper.readValue(p.getChunksJson(), DocumentPage.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize page {} result", p.getPageIndex(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<Integer> getDonePageIndices(IngestionJob job) {
        return new HashSet<>(ingestionJobPageRepository.findPageIndicesByJobAndStatus(
                job, IngestionJobPageStatus.COMPLETED));
    }

    @Transactional(readOnly = true)
    public Optional<IngestionJob> findByUuid(String uuid) {
        return ingestionJobRepository.findByUuid(uuid);
    }

    @Transactional(readOnly = true)
    public List<IngestionJob> findByStatuses(List<IngestionJobStatus> statuses) {
        return ingestionJobRepository.findByStatusIn(statuses);
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
