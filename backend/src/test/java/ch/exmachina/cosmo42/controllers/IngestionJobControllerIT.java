package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.BaseIT;
import ch.exmachina.cosmo42.dto.JobStatusDTO;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.repositories.IngestionJobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionJobControllerIT extends BaseIT {

    @Autowired
    IngestionJobRepository ingestionJobRepository;

    @AfterEach
    void cleanUp() {
        ingestionJobRepository.deleteAll();
    }

    @Test
    void getJobStatus_existingPendingJob_returns200WithZeroProgress() {
        IngestionJob job = save(pendingJob("document.pdf"));

        webTestClient.get()
                .uri("/api/v1/kb/jobs/" + job.getUuid())
                .exchange()
                .expectStatus().isOk()
                .expectBody(JobStatusDTO.class)
                .value(dto -> {
                    assertThat(dto.getJobUuid()).isEqualTo(job.getUuid());
                    assertThat(dto.getStatus()).isEqualTo("PENDING");
                    assertThat(dto.getProgressPercent()).isEqualTo(0);
                    assertThat(dto.getDocumentUuid()).isNull();
                });
    }

    @Test
    void getJobStatus_completedJob_returns200WithProgress100AndDocumentUuid() {
        IngestionJob job = pendingJob("report.pdf");
        job.setStatus(IngestionJobStatus.COMPLETED);
        job.setKbDocumentUuid("kb-doc-uuid");
        job.setCompletedAt(LocalDateTime.now());
        save(job);

        webTestClient.get()
                .uri("/api/v1/kb/jobs/" + job.getUuid())
                .exchange()
                .expectStatus().isOk()
                .expectBody(JobStatusDTO.class)
                .value(dto -> {
                    assertThat(dto.getProgressPercent()).isEqualTo(100);
                    assertThat(dto.getDocumentUuid()).isEqualTo("kb-doc-uuid");
                });
    }

    @Test
    void getJobStatus_failedJob_returns200WithErrorMessage() {
        IngestionJob job = pendingJob("broken.pdf");
        job.setStatus(IngestionJobStatus.FAILED);
        job.setErrorMessage("conversion failed");
        save(job);

        webTestClient.get()
                .uri("/api/v1/kb/jobs/" + job.getUuid())
                .exchange()
                .expectStatus().isOk()
                .expectBody(JobStatusDTO.class)
                .value(dto -> {
                    assertThat(dto.getStatus()).isEqualTo("FAILED");
                    assertThat(dto.getErrorMessage()).isEqualTo("conversion failed");
                });
    }

    @Test
    void getJobStatus_nonExistentUuid_returns404() {
        webTestClient.get()
                .uri("/api/v1/kb/jobs/non-existent-uuid")
                .exchange()
                .expectStatus().isNotFound();
    }

    // --- list endpoint tests ---

    @Test
    void listJobs_noFilter_returnsAllJobs() {
        save(pendingJob("a.pdf"));
        IngestionJob completed = pendingJob("b.pdf");
        completed.setStatus(IngestionJobStatus.COMPLETED);
        completed.setKbDocumentUuid("kb-uuid");
        completed.setCompletedAt(LocalDateTime.now());
        save(completed);

        List<JobStatusDTO> result = webTestClient.get()
                .uri("/api/v1/kb/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(JobStatusDTO.class)
                .returnResult().getResponseBody();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(JobStatusDTO::getStatus)
                .containsExactlyInAnyOrder("PENDING", "COMPLETED");
    }

    @Test
    void listJobs_filterByStatus_returnsOnlyMatchingJobs() {
        save(pendingJob("a.pdf"));
        IngestionJob failed = pendingJob("b.pdf");
        failed.setStatus(IngestionJobStatus.FAILED);
        failed.setErrorMessage("boom");
        save(failed);

        List<JobStatusDTO> result = webTestClient.get()
                .uri("/api/v1/kb/jobs?status=FAILED")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(JobStatusDTO.class)
                .returnResult().getResponseBody();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void listJobs_emptyTable_returnsEmptyList() {
        webTestClient.get()
                .uri("/api/v1/kb/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(JobStatusDTO.class)
                .hasSize(0);
    }

    @Test
    void listJobs_filterByMultipleStatuses_returnsMatchingJobs() {
        save(pendingJob("a.pdf"));
        IngestionJob failed = pendingJob("b.pdf");
        failed.setStatus(IngestionJobStatus.FAILED);
        save(failed);
        IngestionJob completed = pendingJob("c.pdf");
        completed.setStatus(IngestionJobStatus.COMPLETED);
        completed.setKbDocumentUuid("kb-uuid");
        completed.setCompletedAt(LocalDateTime.now());
        save(completed);

        List<JobStatusDTO> result = webTestClient.get()
                .uri("/api/v1/kb/jobs?status=PENDING&status=FAILED")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(JobStatusDTO.class)
                .returnResult().getResponseBody();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(JobStatusDTO::getStatus)
                .containsExactlyInAnyOrder("PENDING", "FAILED");
    }

    // --- helpers ---

    private IngestionJob pendingJob(String fileName) {
        IngestionJob job = new IngestionJob();
        job.setUuid(UUID.randomUUID().toString());
        job.setStatus(IngestionJobStatus.PENDING);
        job.setOriginalFileName(fileName);
        job.setFileSizeBytes(1024L);
        job.setStoredFileUuid(UUID.randomUUID().toString());
        job.setCreatedAt(LocalDateTime.now());
        return job;
    }

    private IngestionJob save(IngestionJob job) {
        return ingestionJobRepository.save(job);
    }

}
