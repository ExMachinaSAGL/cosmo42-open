package ch.exmachina.cosmo42.services;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.ChunkType;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import ch.exmachina.cosmo42.testsupport.Fixtures;

class KBDocumentChunkMergerTest {

    private KBDocumentChunkMerger chunkMerger;

    @BeforeEach
    void setUp() {
        chunkMerger = new KBDocumentChunkMerger();
    }

    @Test
    void mergePages_emptyList_returnsEmpty() {
        assertThat(chunkMerger.mergePages(List.of())).isEmpty();
    }

    @Test
    void mergePages_singlePageNoCutoffs_passesThrough() {
        DocumentPage page = pageWith(chunk("TEXT", "hello", null, false));

        var result = chunkMerger.mergePages(List.of(entry(1, page)));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getContent()).isEqualTo("hello");
    }

    @Test
    void mergePages_textCutoffMergesWithNextPageFirstChunk() {
        DocumentPage page1 = pageWith(chunk("TEXT", "begin", null, true));
        DocumentPage page2 = pageWith(
                chunk("TEXT", "end", null, false),
                chunk("TEXT", "second", null, false));

        var result = chunkMerger.mergePages(List.of(entry(1, page1), entry(2, page2)));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).satisfies(c -> {
        	assertThat(c.getContent()).isEqualTo("begin end");
        	assertThat(c.getStartPage()).isEqualTo(1);
        	assertThat(c.getEndPage()).isEqualTo(2);
        });
        assertThat(result.get(1)).satisfies(c -> {
        	assertThat(c.getContent()).isEqualTo("second");
        	assertThat(c.getStartPage()).isEqualTo(2);
        	assertThat(c.getEndPage()).isEqualTo(2);
        });
    }

    @Test
    void mergePages_cutoffWithDifferentTypeOnNext_noMerge() {
        DocumentPage page1 = pageWith(chunk("TEXT", "para", null, true));
        DocumentPage page2 = pageWith(chunk("TABLE", "| a |", "summary", false));

        var result = chunkMerger.mergePages(List.of(entry(1, page1), entry(2, page2)));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).satisfies(c -> {
        	assertThat(c.getContent()).isEqualTo("para");
        	assertThat(c.getStartPage()).isEqualTo(1);
        	assertThat(c.getEndPage()).isEqualTo(1);
        });

        assertThat(result.get(1)).satisfies(c -> {
        	assertThat(c.getContent()).isEqualTo("| a |");
        	assertThat(c.getSummary()).isEqualTo("summary");
        	assertThat(c.getStartPage()).isEqualTo(2);
        	assertThat(c.getEndPage()).isEqualTo(2);
        });
    }

    @Test
    void mergePages_cutoffAcrossThreePages_mergesAll() {
        DocumentPage page1 = pageWith(chunk("TEXT", "a", null, true));
        DocumentPage page2 = pageWith(chunk("TEXT", "b", null, true));
        DocumentPage page3 = pageWith(chunk("TEXT", "c", null, false));

        var result = chunkMerger.mergePages(List.of(entry(1, page1), entry(2, page2), entry(3, page3)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).satisfies(c -> {
        	assertThat(c.getContent()).isEqualTo("a b c");
        	assertThat(c.getStartPage()).isEqualTo(1);
        	assertThat(c.getEndPage()).isEqualTo(3);
        });
    }
    
    @Test
    void mergePages_cutoffNotMergedIfNonContiguosPages() {
        DocumentPage page1 = pageWith(chunk("TEXT", "a", null, true));
        DocumentPage page2 = pageWith(chunk("TEXT", "b", null, true));
        DocumentPage page4 = pageWith(chunk("TEXT", "c", null, false));

        var result = chunkMerger.mergePages(List.of(entry(1, page1), entry(2, page2), entry(4, page4)));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).satisfies(c -> {
        	assertThat(c.getContent()).isEqualTo("a b");
        	assertThat(c.getStartPage()).isEqualTo(1);
        	assertThat(c.getEndPage()).isEqualTo(2);
        });
        assertThat(result.get(1)).satisfies(c -> {
        	assertThat(c.getContent()).isEqualTo("c");
        	assertThat(c.getStartPage()).isEqualTo(4);
        	assertThat(c.getEndPage()).isEqualTo(4);
        });
    }

    @Test
    void mergePages_tableCutoff_joinsSummaries() {
        DocumentPage page1 = pageWith(chunk("TABLE", "| row1 |", "first half", true));
        DocumentPage page2 = pageWith(chunk("TABLE", "| row2 |", "second half", false));

        var result = chunkMerger.mergePages(List.of(entry(1, page1), entry(2, page2)));

        var merged = result.getFirst();
        assertThat(merged.getContent()).isEqualTo("| row1 | | row2 |");
        assertThat(merged.getSummary()).isEqualTo("first half second half");
        assertThat(merged.getStartPage()).isEqualTo(1);
        assertThat(merged.getEndPage()).isEqualTo(2);
    }

    @Test
    void mergePages_nullOrEmptyPagesAreSkipped() {
        DocumentPage page1 = pageWith(chunk("TEXT", "x", null, false));
        DocumentPage nullChunks = new DocumentPage(null);

        var result = chunkMerger.mergePages(new ArrayList<>(List.of(entry(1, page1), entry(2, nullChunks))));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getContent()).isEqualTo("x");
    }
    

    @Test
    void mergePages_delegatesCorrectly() {
        DocumentPage page1 = Fixtures.page(Fixtures.textChunk("hello"));
        DocumentPage page2 = Fixtures.page(Fixtures.textChunk("world"));

        var result = chunkMerger.mergePages(List.of(entry(1, page1), entry(2, page2)));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("hello");
        assertThat(result.get(1).getContent()).isEqualTo("world");
    }

    private static Chunk chunk(String type, String content, String summary, boolean continues) {
        return new Chunk(ChunkType.valueOf(type), content, summary, continues);
    }

    private static DocumentPage pageWith(Chunk... chunks) {
        return new DocumentPage(new ArrayList<>(List.of(chunks)));
    }
}
