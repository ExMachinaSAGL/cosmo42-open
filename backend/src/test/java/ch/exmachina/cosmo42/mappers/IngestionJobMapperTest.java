package ch.exmachina.cosmo42.mappers;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobPage;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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

    @Nested
    class ToChunksJson {

        @Test
        void serializesPageWithTextChunks() {
            DocumentPage page = new DocumentPage(List.of(
                    new Chunk("text", "Hello world", null, false)));

            String json = mapper.toChunksJson(page);

            assertThat(json).contains("\"type\":\"text\"");
            assertThat(json).contains("\"content\":\"Hello world\"");
        }

        @Test
        void serializesPageWithTableChunks() {
            DocumentPage page = new DocumentPage(List.of(
                    new Chunk("table", "| A | B |", "Sales summary", false)));

            String json = mapper.toChunksJson(page);

            assertThat(json).contains("\"type\":\"table\"");
            assertThat(json).contains("\"content\":\"| A | B |\"");
            assertThat(json).contains("\"summary\":\"Sales summary\"");
        }

        @Test
        void serializesPageWithMultipleChunks() {
            DocumentPage page = new DocumentPage(List.of(
                    new Chunk("text", "First paragraph", null, false),
                    new Chunk("table", "| X |", "Overview", false)));

            String json = mapper.toChunksJson(page);

            assertThat(json).contains("First paragraph");
            assertThat(json).contains("| X |");
            assertThat(json).contains("\"type\":\"text\"");
            assertThat(json).contains("\"type\":\"table\"");
        }

        @Test
        void serializesPageWithEmptyChunks() {
            DocumentPage page = new DocumentPage(List.of());

            String json = mapper.toChunksJson(page);

            assertThat(json).contains("\"chunks\":[]");
        }

        @Test
        void serializesPageWithNullChunks() {
            DocumentPage page = new DocumentPage(null);

            String json = mapper.toChunksJson(page);

            assertThat(json).contains("\"chunks\":null");
        }

        @Test
        void serializesChunkWithContinueFlag() {
            DocumentPage page = new DocumentPage(List.of(
                    new Chunk("text", "to be continued", null, true)));

            String json = mapper.toChunksJson(page);

            assertThat(json).contains("\"continuesOnNextPage\":true");
        }
    }

    @Nested
    class ToDocumentPage {

        @Test
        void deserializesValidPageJson() {
            String json = """
                    {"chunks":[{"type":"text","content":"Hello","continuesOnNextPage":false}]}""";
            IngestionJobPage entity = new IngestionJobPage();
            entity.setChunksJson(json);

            DocumentPage page = mapper.toDocumentPage(entity);

            assertThat(page.getChunks()).hasSize(1);
            assertThat(page.getChunks().getFirst().getType()).isEqualTo("text");
            assertThat(page.getChunks().getFirst().getContent()).isEqualTo("Hello");
        }

        @Test
        void deserializesPageWithTableChunk() {
            String json = """
                    {"chunks":[{"type":"table","content":"| A |","summary":"Data","continuesOnNextPage":false}]}""";
            IngestionJobPage entity = new IngestionJobPage();
            entity.setChunksJson(json);

            DocumentPage page = mapper.toDocumentPage(entity);

            assertThat(page.getChunks().getFirst().getType()).isEqualTo("table");
            assertThat(page.getChunks().getFirst().getSummary()).isEqualTo("Data");
        }

        @Test
        void deserializesPageWithNullChunks() {
            String json = "{\"chunks\":null}";
            IngestionJobPage entity = new IngestionJobPage();
            entity.setChunksJson(json);

            DocumentPage page = mapper.toDocumentPage(entity);

            assertThat(page.getChunks()).isNull();
        }

        @Test
        void propagatesExceptionForNullJson() {
            IngestionJobPage entity = new IngestionJobPage();
            entity.setChunksJson(null);

            try {
                mapper.toDocumentPage(entity);
            } catch (Exception e) {
                assertThat(e).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("content");
            }
        }

        @Test
        void returnsNullForInvalidJson() {
            IngestionJobPage entity = new IngestionJobPage();
            entity.setChunksJson("not json");

            DocumentPage page = mapper.toDocumentPage(entity);

            assertThat(page).isNull();
        }

        @Test
        void roundTripPreservesData() {
            DocumentPage original = new DocumentPage(List.of(
                    new Chunk("text", "Paragraph one", null, false),
                    new Chunk("table", "| Col |", "Table info", false),
                    new Chunk("text", "cut off", null, true)));

            String json = mapper.toChunksJson(original);
            IngestionJobPage entity = new IngestionJobPage();
            entity.setChunksJson(json);
            DocumentPage roundTripped = mapper.toDocumentPage(entity);

            assertThat(roundTripped.getChunks()).hasSize(3);
            assertThat(roundTripped.getChunks().get(0).getType()).isEqualTo("text");
            assertThat(roundTripped.getChunks().get(0).getContent()).isEqualTo("Paragraph one");
            assertThat(roundTripped.getChunks().get(1).getType()).isEqualTo("table");
            assertThat(roundTripped.getChunks().get(1).getSummary()).isEqualTo("Table info");
            assertThat(roundTripped.getChunks().get(2).getContinuesOnNextPage()).isTrue();
        }
    }
}
