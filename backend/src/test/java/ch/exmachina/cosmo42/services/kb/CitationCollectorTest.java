package ch.exmachina.cosmo42.services.kb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.exmachina.cosmo42.dto.CitationEntryDTO;
import ch.exmachina.cosmo42.services.chat.tools.KBDocumentSimilaritySearchTool.ChunkDTO;

class CitationCollectorTest {

    private CitationCollector collector = new CitationCollector();

    @Test
    void collectCitations() {
        collector.collectCitations("see __CITE_1__, __CITE_3__");
        collector.collectChunks(List.of(
            ChunkDTO.builder().id(1).uuid("CITE_1").fileName("cite1.pdf").sourcePages(new int[] { 1, 2 }).build(),
            ChunkDTO.builder().id(2).uuid("CITE_2").fileName("cite1.pdf").sourcePages(new int[] { 3, 4 }).build(),
            ChunkDTO.builder().id(3).uuid("CITE_3").fileName("cite2.pdf").sourcePages(new int[] { 5 }).build()));

        assertThat(collector.assemble())
            .usingRecursiveFieldByFieldElementComparator()
            .containsExactly(
                CitationEntryDTO.builder()
                    .id(1).originalIndex(1).uuid("CITE_1").fileName("cite1.pdf").sourcePages(new int[] { 1, 2 })
                    .build(),
                CitationEntryDTO.builder()
                    .id(2).originalIndex(3).uuid("CITE_3").fileName("cite2.pdf").sourcePages(new int[] { 5 }).build());
    }

}
