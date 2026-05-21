package ch.exmachina.cosmo42.integration;

import ch.exmachina.cosmo42.AbstractWebIntegrationTest;
import ch.exmachina.cosmo42.dto.DocumentDTO;
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

class KBDocumentsControllerIT extends AbstractWebIntegrationTest {

    @Autowired
    IngestionJobRepository ingestionJobRepository;

    @AfterEach
    void cleanUp() {
        ingestionJobRepository.deleteAll();
    }

    @Test
    void getDocument_existingPendingJob_returns200() {
        IngestionJob job = save(pendingJob("document.pdf"));

        webTestClient.get()
                .uri("/api/v1/kb/documents/" + job.getStoredFileUuid())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DocumentDTO.class)
                .value(dto -> {
                    assertThat(dto.getFileUuid()).isEqualTo(job.getStoredFileUuid());
                    assertThat(dto.getFileName()).isEqualTo(job.getOriginalFileName());
                    assertThat(dto.getUploadedAt()).isNotNull();
                    assertThat(dto.getStatus()).isEqualTo("loading");
                });
    }

    @Test
    void getDocument_completedJob_returns200() {
        IngestionJob job = pendingJob("report.pdf");
        job.setStatus(IngestionJobStatus.COMPLETED);
        job.setKbDocumentUuid("kb-doc-uuid");
        job.setCompletedAt(LocalDateTime.now());
        save(job);

        webTestClient.get()
                .uri("/api/v1/kb/documents/" + job.getStoredFileUuid())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DocumentDTO.class)
                .value(dto -> {
                    assertThat(dto.getStatus()).isEqualTo("done");
                });
    }

    @Test
    void getDocument_failedJob_returns200WithErrorMessage() {
        IngestionJob job = pendingJob("broken.pdf");
        job.setStatus(IngestionJobStatus.FAILED);
        job.setErrorMessage("conversion failed");
        save(job);

        webTestClient.get()
                .uri("/api/v1/kb/documents/" + job.getStoredFileUuid())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DocumentDTO.class)
                .value(dto -> {
                    assertThat(dto.getStatus()).isEqualTo("error");
                    assertThat(dto.getErrorMessage()).isEqualTo("conversion failed");
                });
    }

    @Test
    void getDocument_nonExistentUuid_returns404() {
        webTestClient.get()
                .uri("/api/v1/kb/documents/non-existent-uuid")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getDocuments_returnsAllDocumentsWithFrontendStatus() {
        save(pendingJob("a.pdf"));
        IngestionJob completed = pendingJob("b.pdf");
        completed.setStatus(IngestionJobStatus.COMPLETED);
        completed.setKbDocumentUuid("kb-uuid");
        completed.setCompletedAt(LocalDateTime.now());
        save(completed);

        List<DocumentDTO> result = webTestClient.get()
                .uri("/api/v1/kb/documents")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DocumentDTO.class)
                .returnResult().getResponseBody();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DocumentDTO::getStatus)
                .containsExactlyInAnyOrder("loading", "done");
    }

    @Test
    void getDocuments_emptyTable_returnsEmptyList() {
        webTestClient.get()
                .uri("/api/v1/kb/documents")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DocumentDTO.class)
                .hasSize(0);
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
