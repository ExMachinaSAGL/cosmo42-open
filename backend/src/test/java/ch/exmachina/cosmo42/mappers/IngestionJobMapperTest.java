package ch.exmachina.cosmo42.mappers;

import ch.exmachina.cosmo42.dto.JobStatusDTO;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

class IngestionJobMapperTest {

    IngestionJobMapper mapper = new IngestionJobMapper();

    @Test
    void toEntity_setsAllFieldsWithPendingStatus() {
        IngestionJob job = mapper.toEntity("report.pdf", 2048L, "file-uuid");

        assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.PENDING);
        assertThat(job.getOriginalFileName()).isEqualTo("report.pdf");
        assertThat(job.getFileSizeBytes()).isEqualTo(2048L);
        assertThat(job.getStoredFileUuid()).isEqualTo("file-uuid");
        assertThat(job.getUuid()).isNotBlank();
        assertThat(job.getCreatedAt()).isCloseTo(LocalDateTime.now(), within(2, ChronoUnit.SECONDS));
    }

    @Test
    void toStatusDTO_mapsAllFields() {
        IngestionJob job = new IngestionJob();
        job.setUuid("job-uuid");
        job.setStatus(IngestionJobStatus.PROCESSING);
        job.setKbDocumentUuid("doc-uuid");
        job.setErrorMessage("some error");

        JobStatusDTO dto = mapper.toStatusDTO(job, 42);

        assertThat(dto.getJobUuid()).isEqualTo("job-uuid");
        assertThat(dto.getStatus()).isEqualTo("PROCESSING");
        assertThat(dto.getProgressPercent()).isEqualTo(42);
        assertThat(dto.getErrorMessage()).isEqualTo("some error");
    }

    @Test
    void toStatusDTO_completedJob_exposesDocumentUuid() {
        IngestionJob job = new IngestionJob();
        job.setUuid("job-uuid");
        job.setStatus(IngestionJobStatus.COMPLETED);
        job.setKbDocumentUuid("doc-uuid");

        JobStatusDTO dto = mapper.toStatusDTO(job, 100);

        assertThat(dto.getDocumentUuid()).isEqualTo("doc-uuid");
    }

    @Test
    void toStatusDTO_nonCompletedJob_hidesDocumentUuid() {
        IngestionJob job = new IngestionJob();
        job.setUuid("job-uuid");
        job.setStatus(IngestionJobStatus.PROCESSING);
        job.setKbDocumentUuid("doc-uuid");

        JobStatusDTO dto = mapper.toStatusDTO(job, 50);

        assertThat(dto.getDocumentUuid()).isNull();
    }

    @Test
    void toStatusDTO_nullErrorMessage_propagatesNull() {
        IngestionJob job = new IngestionJob();
        job.setUuid("job-uuid");
        job.setStatus(IngestionJobStatus.PENDING);

        JobStatusDTO dto = mapper.toStatusDTO(job, 0);

        assertThat(dto.getErrorMessage()).isNull();
    }
}
