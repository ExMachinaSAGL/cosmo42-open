package ch.exmachina.cosmo42.mappers;

import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.entities.KBDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KBDocumentMapperTest {

    KBDocumentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new KBDocumentMapper();
    }

    @Test
    void mapsAllFieldsFromEntityToDto() {
        KBDocument doc = new KBDocument();
        doc.setUuid("doc-uuid-123");
        doc.setFileName("report.pdf");

        DocumentDTO dto = mapper.toDocumentDTO(doc);

        assertThat(dto.getUuid()).isEqualTo("doc-uuid-123");
        assertThat(dto.getName()).isEqualTo("report.pdf");
        assertThat(dto.getContent()).isNull();
    }

    @Test
    void producesDistinctDtoForEachEntity() {
        KBDocument doc1 = new KBDocument();
        doc1.setUuid("u-1");
        doc1.setFileName("a.pdf");
        KBDocument doc2 = new KBDocument();
        doc2.setUuid("u-2");
        doc2.setFileName("b.pdf");

        DocumentDTO dto1 = mapper.toDocumentDTO(doc1);
        DocumentDTO dto2 = mapper.toDocumentDTO(doc2);

        assertThat(dto1.getUuid()).isEqualTo("u-1");
        assertThat(dto2.getUuid()).isEqualTo("u-2");
        assertThat(dto1).isNotEqualTo(dto2);
    }

    @Test
    void nullFieldsMappedAsNull() {
        KBDocument doc = new KBDocument();

        DocumentDTO dto = mapper.toDocumentDTO(doc);

        assertThat(dto.getUuid()).isNull();
        assertThat(dto.getName()).isNull();
    }

    @Test
    void builderSetsOnlyExpectedFields() {
        KBDocument doc = new KBDocument();
        doc.setUuid("test-uuid");
        doc.setFileName("name.pdf");
        doc.setFileSize(999L);
        doc.setCreationTimestamp(java.time.LocalDateTime.of(2026, 1, 1, 12, 0));

        DocumentDTO dto = mapper.toDocumentDTO(doc);

        assertThat(dto.getUuid()).isEqualTo("test-uuid");
        assertThat(dto.getName()).isEqualTo("name.pdf");
        // Content is never set by mapper — always null until explicitly set by caller.
        assertThat(dto.getContent()).isNull();
    }
}
