package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.entities.KBDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class IngestionJobRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    IngestionJobRepository repository;
    @Autowired
    KBDocumentRepository kbDocumentRepository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
        kbDocumentRepository.deleteAll();
    }

    @Test
    void findByUuidReturnsJobWhenPresent() {
        IngestionJob job = newJob("job-uuid", "file-1", IngestionJobStatus.PENDING);
        repository.save(job);

        var found = repository.findByUuid("job-uuid");

        assertThat(found).isPresent();
        assertThat(found.get().getStoredFileUuid()).isEqualTo("file-1");
    }

    @Test
    void findByUuidReturnsEmptyWhenMissing() {
        assertThat(repository.findByUuid("nonexistent")).isEmpty();
    }

    @Test
    void findByStoredFileUuidReturnsJobWhenPresent() {
        IngestionJob job = newJob("job-1", "stored-file-123", IngestionJobStatus.COMPLETED);
        repository.save(job);

        var found = repository.findByStoredFileUuid("stored-file-123");

        assertThat(found).isPresent();
        assertThat(found.get().getUuid()).isEqualTo("job-1");
        assertThat(found.get().getStatus()).isEqualTo(IngestionJobStatus.COMPLETED);
    }

    @Test
    void findByStoredFileUuidReturnsEmptyWhenMissing() {
        assertThat(repository.findByStoredFileUuid("no-such-file")).isEmpty();
    }

    @Test
    void findByStatusInReturnsMatchingJobs() {
        IngestionJob pending = newJob("j1", "f1", IngestionJobStatus.PENDING);
        IngestionJob processing = newJob("j2", "f2", IngestionJobStatus.PROCESSING);
        IngestionJob completed = newJob("j3", "f3", IngestionJobStatus.COMPLETED);
        repository.saveAll(List.of(pending, processing, completed));

        var stuck = repository.findByStatusIn(List.of(IngestionJobStatus.PENDING, IngestionJobStatus.INTERRUPTED));

        assertThat(stuck).hasSize(1);
        assertThat(stuck.getFirst().getUuid()).isEqualTo("j1");
    }

    @Test
    void findByStatusInReturnsAllMatchingMultipleStatuses() {
        IngestionJob interrupted = newJob("j1", "f1", IngestionJobStatus.INTERRUPTED);
        IngestionJob pending = newJob("j2", "f2", IngestionJobStatus.PENDING);
        IngestionJob completed = newJob("j3", "f3", IngestionJobStatus.COMPLETED);
        repository.saveAll(List.of(interrupted, pending, completed));

        var stuck = repository.findByStatusIn(
                List.of(IngestionJobStatus.PENDING, IngestionJobStatus.INTERRUPTED));

        assertThat(stuck).hasSize(2);
    }

    @Test
    void findByStatusInReturnsEmptyWhenNoMatches() {
        IngestionJob completed = newJob("j1", "f1", IngestionJobStatus.COMPLETED);
        repository.save(completed);

        var result = repository.findByStatusIn(List.of(IngestionJobStatus.FAILED));

        assertThat(result).isEmpty();
    }

    @Test
    void deleteByKbDocumentUuidRemovesJobWithRelatedDocument() {
        KBDocument doc = newDocument();
        kbDocumentRepository.save(doc);
        IngestionJob job = newJob("job-uuid", "file-uuid", IngestionJobStatus.COMPLETED);
        job.setKbDocumentUuid("doc-uuid");
        repository.save(job);

        repository.deleteByKbDocumentUuid("doc-uuid");
        repository.flush();

        assertThat(repository.findByUuid("job-uuid")).isEmpty();
    }

    @Test
    void deleteByKbDocumentUuidDoesNotAffectUnrelatedJobs() {
        KBDocument doc = newDocument();
        kbDocumentRepository.save(doc);
        IngestionJob job1 = newJob("job-1", "file-1", IngestionJobStatus.COMPLETED);
        job1.setKbDocumentUuid("doc-uuid");
        IngestionJob job2 = newJob("job-2", "file-2", IngestionJobStatus.PENDING);
        job2.setKbDocumentUuid("other-doc");
        repository.saveAll(List.of(job1, job2));

        repository.deleteByKbDocumentUuid("doc-uuid");
        repository.flush();

        assertThat(repository.findByUuid("job-1")).isEmpty();
        assertThat(repository.findByUuid("job-2")).isPresent();
    }

    private IngestionJob newJob(String uuid, String storedFileUuid, IngestionJobStatus status) {
        IngestionJob job = new IngestionJob();
        job.setUuid(uuid);
        job.setStoredFileUuid(storedFileUuid);
        job.setStatus(status);
        job.setOriginalFileName("doc.pdf");
        job.setFileSizeBytes(1024L);
        job.setCreatedAt(LocalDateTime.now());
        job.setChunksEmbedded(false);
        return job;
    }

    private KBDocument newDocument() {
        KBDocument d = new KBDocument();
        d.setUuid("doc-uuid");
        d.setFileName("report.pdf");
        d.setFileSize(1L);
        d.setCreationTimestamp(LocalDateTime.now());
        return d;
    }
}
