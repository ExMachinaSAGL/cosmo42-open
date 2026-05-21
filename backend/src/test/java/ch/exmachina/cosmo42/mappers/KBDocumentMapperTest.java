package ch.exmachina.cosmo42.mappers;

import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.dto.DownloadDocumentDTO;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.entities.KBDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KBDocumentMapperTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 15, 9, 30);

    KBDocumentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new KBDocumentMapper();
    }

    private IngestionJob baseJob(IngestionJobStatus status) {
        IngestionJob j = new IngestionJob();
        j.setUuid("job-1");
        j.setStoredFileUuid("file-1");
        j.setOriginalFileName("report.pdf");
        j.setFileSizeBytes(123L);
        j.setStatus(status);
        j.setCreatedAt(CREATED_AT);
        j.setChunksEmbedded(false);
        return j;
    }

    @Test
    void toDocumentDTO_pendingJob_yieldsLoadingStatusAndZeroProgress() {
        DocumentDTO dto = mapper.toDocumentDTO(baseJob(IngestionJobStatus.PENDING));

        assertThat(dto.getFileUuid()).isEqualTo("file-1");
        assertThat(dto.getFileName()).isEqualTo("report.pdf");
        assertThat(dto.getStatus()).isEqualTo("loading");
        assertThat(dto.getUploadedAt()).isEqualTo(CREATED_AT);
        assertThat(dto.getErrorMessage()).isNull();
        assertThat(dto.getProgressPercent()).isEqualTo(0);
    }

    @Test
    void toDocumentDTO_processingJobWithoutPagesYields10Percent() {
        DocumentDTO dto = mapper.toDocumentDTO(baseJob(IngestionJobStatus.PROCESSING));

        assertThat(dto.getStatus()).isEqualTo("loading");
        assertThat(dto.getProgressPercent()).isEqualTo(10);
    }

    @Test
    void toDocumentDTO_processingJobWithKnownPagesYields50Percent() {
        IngestionJob job = baseJob(IngestionJobStatus.PROCESSING);
        job.setTotalPages(5);

        DocumentDTO dto = mapper.toDocumentDTO(job);

        assertThat(dto.getProgressPercent()).isEqualTo(50);
    }

    @Test
    void toDocumentDTO_processingJobWithEmbeddedChunksYields95Percent() {
        IngestionJob job = baseJob(IngestionJobStatus.PROCESSING);
        job.setTotalPages(5);
        job.setChunksEmbedded(true);

        DocumentDTO dto = mapper.toDocumentDTO(job);

        assertThat(dto.getProgressPercent()).isEqualTo(95);
    }

    @Test
    void toDocumentDTO_interruptedJobYieldsLoadingStatus() {
        DocumentDTO dto = mapper.toDocumentDTO(baseJob(IngestionJobStatus.INTERRUPTED));

        assertThat(dto.getStatus()).isEqualTo("loading");
        assertThat(dto.getProgressPercent()).isEqualTo(10);
    }

    @Test
    void toDocumentDTO_completedJobYieldsDoneAnd100Percent() {
        DocumentDTO dto = mapper.toDocumentDTO(baseJob(IngestionJobStatus.COMPLETED));

        assertThat(dto.getStatus()).isEqualTo("loaded");
        assertThat(dto.getProgressPercent()).isEqualTo(100);
        assertThat(dto.getErrorMessage()).isNull();
    }

    @Test
    void toDocumentDTO_failedJobCarriesErrorMessageAndNullProgress() {
        IngestionJob job = baseJob(IngestionJobStatus.FAILED);
        job.setErrorMessage("LLM unreachable");

        DocumentDTO dto = mapper.toDocumentDTO(job);

        assertThat(dto.getStatus()).isEqualTo("error");
        assertThat(dto.getErrorMessage()).isEqualTo("LLM unreachable");
        assertThat(dto.getProgressPercent()).isNull();
    }

    @Test
    void toDocumentDTO_nonFailedStatusOmitsErrorMessageEvenWhenSet() {
        IngestionJob job = baseJob(IngestionJobStatus.COMPLETED);
        job.setErrorMessage("stale error");

        DocumentDTO dto = mapper.toDocumentDTO(job);

        assertThat(dto.getErrorMessage()).isNull();
    }

    @Test
    void toDownloadDocumentDTO_mapsKBDocumentMetadataAndLeavesContentNull() {
        KBDocument doc = new KBDocument();
        doc.setUuid("doc-uuid");
        doc.setFileName("report.pdf");
        doc.setCreationTimestamp(CREATED_AT);

        DownloadDocumentDTO dto = mapper.toDownloadDocumentDTO(doc);

        assertThat(dto.getFileUuid()).isEqualTo("doc-uuid");
        assertThat(dto.getFileName()).isEqualTo("report.pdf");
        assertThat(dto.getUploadedAt()).isEqualTo(CREATED_AT);
        assertThat(dto.getContent()).isNull();
    }
}
