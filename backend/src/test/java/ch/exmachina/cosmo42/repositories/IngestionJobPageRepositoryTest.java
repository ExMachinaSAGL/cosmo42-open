package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobPage;
import ch.exmachina.cosmo42.entities.IngestionJobPageStatus;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.entities.KBDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class IngestionJobPageRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    IngestionJobPageRepository repository;
    @Autowired
    IngestionJobRepository jobRepository;
    @Autowired
    KBDocumentRepository kbDocumentRepository;

    private IngestionJob job;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
        jobRepository.deleteAll();
        kbDocumentRepository.deleteAll();
        job = createJob("job-1", "file-1");
    }

    @Test
    void findByJobOrderByPageIndexAscReturnsSortedPages() {
        repository.save(newPage(job, 2, IngestionJobPageStatus.COMPLETED));
        repository.save(newPage(job, 0, IngestionJobPageStatus.COMPLETED));
        repository.save(newPage(job, 1, IngestionJobPageStatus.PENDING));

        List<IngestionJobPage> pages = repository.findByJobOrderByPageIndexAsc(job);

        assertThat(pages).hasSize(3);
        assertThat(pages.get(0).getPageIndex()).isEqualTo(0);
        assertThat(pages.get(1).getPageIndex()).isEqualTo(1);
        assertThat(pages.get(2).getPageIndex()).isEqualTo(2);
    }

    @Test
    void findByJobAndPageIndexReturnsCorrectPage() {
        repository.save(newPage(job, 3, IngestionJobPageStatus.PENDING));

        var found = repository.findByJobAndPageIndex(job, 3);

        assertThat(found).isPresent();
        assertThat(found.get().getPageIndex()).isEqualTo(3);
        assertThat(found.get().getStatus()).isEqualTo(IngestionJobPageStatus.PENDING);
    }

    @Test
    void findByJobAndPageIndexReturnsEmptyWhenMissing() {
        assertThat(repository.findByJobAndPageIndex(job, 99)).isEmpty();
    }

    @Test
    void findByJobUuidAndPageIndexReturnsCorrectPage() {
        repository.save(newPage(job, 1, IngestionJobPageStatus.COMPLETED));

        var found = repository.findByJob_UuidAndPageIndex("job-1", 1);

        assertThat(found).isPresent();
        assertThat(found.get().getPageIndex()).isEqualTo(1);
    }

    @Test
    void findByJobUuidAndPageIndexReturnsEmptyWhenMissing() {
        assertThat(repository.findByJob_UuidAndPageIndex("job-1", 99)).isEmpty();
    }

    @Test
    void existsByJobAndPageIndexReturnsTrueWhenPresent() {
        repository.save(newPage(job, 0, IngestionJobPageStatus.PENDING));

        assertThat(repository.existsByJobAndPageIndex(job, 0)).isTrue();
    }

    @Test
    void existsByJobAndPageIndexReturnsFalseWhenMissing() {
        assertThat(repository.existsByJobAndPageIndex(job, 999)).isFalse();
    }

    @Test
    void countByJobAndStatusCountsCorrectly() {
        repository.save(newPage(job, 0, IngestionJobPageStatus.COMPLETED));
        repository.save(newPage(job, 1, IngestionJobPageStatus.COMPLETED));
        repository.save(newPage(job, 2, IngestionJobPageStatus.FAILED));

        assertThat(repository.countByJobAndStatus(job, IngestionJobPageStatus.COMPLETED)).isEqualTo(2);
        assertThat(repository.countByJobAndStatus(job, IngestionJobPageStatus.FAILED)).isEqualTo(1);
        assertThat(repository.countByJobAndStatus(job, IngestionJobPageStatus.PENDING)).isEqualTo(0);
    }

    @Test
    void findRetryablePageIndicesReturnsPendingPages() {
        repository.save(newPage(job, 0, IngestionJobPageStatus.PENDING));
        repository.save(newPage(job, 1, IngestionJobPageStatus.COMPLETED));
        repository.save(newPage(job, 2, IngestionJobPageStatus.PENDING));

        List<Integer> retryable = repository.findRetryablePageIndices(job, 3);

        assertThat(retryable).containsExactlyInAnyOrder(0, 2);
    }

    @Test
    void findRetryablePageIndicesReturnsFailedPagesUnderMaxAttempts() {
        IngestionJobPage p = newPage(job, 0, IngestionJobPageStatus.FAILED);
        p.setAttemptCount(2);
        IngestionJobPage p2 = newPage(job, 1, IngestionJobPageStatus.COMPLETED);
        repository.saveAll(List.of(p, p2));

        List<Integer> retryable = repository.findRetryablePageIndices(job, 3);

        assertThat(retryable).containsExactly(0);
    }

    @Test
    void findRetryablePageIndicesExcludesFailedPagesAtMaxAttempts() {
        IngestionJobPage p = newPage(job, 0, IngestionJobPageStatus.FAILED);
        p.setAttemptCount(3);
        repository.save(p);

        List<Integer> retryable = repository.findRetryablePageIndices(job, 3);

        assertThat(retryable).isEmpty();
    }

    @Test
    void countExhaustedFailuresCountsPagesAtOrAboveMaxAttempts() {
        IngestionJobPage exhausted = newPage(job, 0, IngestionJobPageStatus.FAILED);
        exhausted.setAttemptCount(5);
        IngestionJobPage retryable = newPage(job, 1, IngestionJobPageStatus.FAILED);
        retryable.setAttemptCount(2);
        IngestionJobPage completed = newPage(job, 2, IngestionJobPageStatus.COMPLETED);
        repository.saveAll(List.of(exhausted, retryable, completed));

        long count = repository.countExhaustedFailures(job, 3);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void countExhaustedFailuresReturnsZeroWhenNoneExhausted() {
        IngestionJobPage retryable = newPage(job, 0, IngestionJobPageStatus.FAILED);
        retryable.setAttemptCount(1);
        repository.save(retryable);

        long count = repository.countExhaustedFailures(job, 3);

        assertThat(count).isEqualTo(0);
    }

    @Test
    void deleteByJobKbDocumentUuidRemovesPagesForDocument() {
        KBDocument doc = newDocument("doc-uuid", "report.pdf");
        kbDocumentRepository.save(doc);
        job.setKbDocumentUuid("doc-uuid");
        jobRepository.save(job);
        repository.save(newPage(job, 0, IngestionJobPageStatus.COMPLETED));
        repository.save(newPage(job, 1, IngestionJobPageStatus.COMPLETED));

        repository.deleteByJob_kbDocumentUuid("doc-uuid");
        repository.flush();

        assertThat(repository.findByJobOrderByPageIndexAsc(job)).isEmpty();
    }

    private IngestionJobPage newPage(IngestionJob job, int pageIndex, IngestionJobPageStatus status) {
        IngestionJobPage page = new IngestionJobPage();
        page.setJob(job);
        page.setPageIndex(pageIndex);
        page.setStatus(status);
        return page;
    }

    private IngestionJob createJob(String uuid, String storedFileUuid) {
        IngestionJob j = new IngestionJob();
        j.setUuid(uuid);
        j.setStoredFileUuid(storedFileUuid);
        j.setStatus(IngestionJobStatus.PROCESSING);
        j.setOriginalFileName("doc.pdf");
        j.setFileSizeBytes(1024L);
        j.setCreatedAt(LocalDateTime.now());
        j.setChunksEmbedded(false);
        return jobRepository.save(j);
    }

    private KBDocument newDocument(String uuid, String fileName) {
        KBDocument d = new KBDocument();
        d.setUuid(uuid);
        d.setFileName(fileName);
        d.setFileSize(1L);
        d.setCreationTimestamp(LocalDateTime.now());
        return d;
    }
}
