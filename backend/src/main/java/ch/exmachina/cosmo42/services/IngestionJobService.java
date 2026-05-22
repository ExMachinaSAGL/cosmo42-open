package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.entities.*;
import ch.exmachina.cosmo42.mappers.IngestionJobMapper;
import ch.exmachina.cosmo42.repositories.IngestionJobPageRepository;
import ch.exmachina.cosmo42.repositories.IngestionJobRepository;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class IngestionJobService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;

    IngestionJobRepository ingestionJobRepository;
    IngestionJobPageRepository ingestionJobPageRepository;
    IngestionJobMapper ingestionJobMapper;

    @Transactional
    public IngestionJob createJob(String originalFileName, long fileSizeBytes, String storedFileUuid) {
        return ingestionJobRepository.save(ingestionJobMapper.toEntity(originalFileName, fileSizeBytes, storedFileUuid));
    }

    @Transactional
    public void markProcessing(String jobUuid) {
        IngestionJob job = ingestionJobRepository.findByUuid(jobUuid).orElseThrow();
        job.setStatus(IngestionJobStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        ingestionJobRepository.save(job);
    }

    @Transactional
    public void markInterrupted(String jobUuid) {
        IngestionJob job = ingestionJobRepository.findByUuid(jobUuid).orElseThrow();
        job.setStatus(IngestionJobStatus.INTERRUPTED);
        ingestionJobRepository.save(job);
    }

    @Transactional
    public void markCompleted(String jobUuid) {
        IngestionJob job = ingestionJobRepository.findByUuid(jobUuid).orElseThrow();
        job.setStatus(IngestionJobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        ingestionJobRepository.save(job);
    }

    @Transactional
    public void markFailed(String jobUuid, String errorMessage) {
        String truncated = errorMessage != null
                ? errorMessage.substring(0, Math.min(errorMessage.length(), ERROR_MESSAGE_MAX_LENGTH))
                : null;
        IngestionJob job = ingestionJobRepository.findByUuid(jobUuid).orElseThrow();
        job.setStatus(IngestionJobStatus.FAILED);
        job.setErrorMessage(truncated);
        ingestionJobRepository.save(job);
    }

    @Transactional
    public void markChunksEmbedded(String jobUuid) {
        IngestionJob job = ingestionJobRepository.findByUuid(jobUuid).orElseThrow();
        job.setChunksEmbedded(true);
        ingestionJobRepository.save(job);
    }

    @Transactional
    public void setTotalPages(String jobUuid, int totalPages) {
        IngestionJob job = ingestionJobRepository.findByUuid(jobUuid).orElseThrow();
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
        IngestionJob job = ingestionJobRepository.findByUuid(jobUuid).orElseThrow();
        job.setKbDocumentUuid(kbDocumentUuid);
        ingestionJobRepository.save(job);
    }

    @Transactional
    public void savePageResult(String jobUuid, int pageIndex, DocumentPage page) {
        IngestionJobPage entity = ingestionJobPageRepository.findByJob_UuidAndPageIndex(jobUuid, pageIndex).orElse(null);
        if (entity == null) {
            log.warn("Page {} not found for job {} while saving result", pageIndex, jobUuid);
            return;
        }
        entity.setAttemptCount(entity.getAttemptCount() + 1);
        if (page == null || page.getChunks() == null) {
            entity.setStatus(IngestionJobPageStatus.FAILED);
            ingestionJobPageRepository.save(entity);
            return;
        }
        String json = ingestionJobMapper.toChunksJson(page);
        if (json != null) {
            entity.setChunksJson(json);
            entity.setStatus(IngestionJobPageStatus.COMPLETED);
        } else {
            entity.setStatus(IngestionJobPageStatus.FAILED);
        }
        ingestionJobPageRepository.save(entity);
    }

    @Transactional
    public void clearCompletedPagesChunksJson(IngestionJob job) {
        List<IngestionJobPage> completed = ingestionJobPageRepository.findByJobOrderByPageIndexAsc(job).stream()
                .filter(p -> p.getStatus() == IngestionJobPageStatus.COMPLETED)
                .toList();
        completed.forEach(p -> p.setChunksJson(null));
        ingestionJobPageRepository.saveAll(completed);
    }

    @Transactional(readOnly = true)
    public List<DocumentPage> loadCompletedPages(IngestionJob job) {
        return ingestionJobPageRepository
                .findByJobOrderByPageIndexAsc(job).stream()
                .filter(p -> p.getStatus() == IngestionJobPageStatus.COMPLETED)
                .map(ingestionJobMapper::toDocumentPage)
                .filter(Objects::nonNull)
                .toList();
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
}
