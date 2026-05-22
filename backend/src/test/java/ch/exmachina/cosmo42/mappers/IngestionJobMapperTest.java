package ch.exmachina.cosmo42.mappers;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IngestionJobMapperTest {

    IngestionJobMapper mapper = new IngestionJobMapper(new ObjectMapper());

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
}
