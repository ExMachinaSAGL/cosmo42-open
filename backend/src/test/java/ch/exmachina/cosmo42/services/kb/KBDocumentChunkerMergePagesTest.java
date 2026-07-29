package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.ChunkType;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.ArrayList;
import java.util.List;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KBDocumentChunkerMergePagesTest {

    private KBDocumentChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new KBDocumentChunker(mock(ChatModel.class), OpenAiChatOptions.builder(), 1, 600);
    }

    @Test
    void mergePages_emptyList_returnsEmpty() {
        assertThat(chunker.mergePages(List.of())).isEmpty();
    }

    @Test
    void mergePages_singlePageNoCutoffs_passesThrough() {
        DocumentPage page = pageWith(chunk("text", "hello", null, false));

        var result = chunker.mergePages(List.of(entry(1, page)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChunks()).hasSize(1);
        assertThat(result.get(0).getChunks().get(0).getContent()).isEqualTo("hello");
    }

    @Test
    void mergePages_textCutoffMergesWithNextPageFirstChunk() {
        DocumentPage page1 = pageWith(chunk("text", "begin", null, true));
        DocumentPage page2 = pageWith(
                chunk("text", "end", null, false),
                chunk("text", "second", null, false));

        var result = chunker.mergePages(List.of(entry(1, page1), entry(2, page2)));

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getChunks()).hasSize(1);
        assertThat(result.get(0).getChunks().getFirst().getContent()).isEqualTo("begin end");
        assertThat(result.get(0).getChunks().getFirst().getContinuesOnNextPage()).isFalse();
        assertThat(result.get(1).getChunks()).hasSize(1);
        assertThat(result.get(1).getChunks().getFirst().getContent()).isEqualTo("second");
    }

    @Test
    void mergePages_cutoffWithDifferentTypeOnNext_noMerge() {
        DocumentPage page1 = pageWith(chunk("text", "para", null, true));
        DocumentPage page2 = pageWith(chunk("table", "| a |", "summary", false));

        var result = chunker.mergePages(List.of(entry(1, page1), entry(2, page2)));

        assertThat(result.get(0).getChunks().getFirst().getContent()).isEqualTo("para");
        assertThat(result.get(1).getChunks().getFirst().getContent()).isEqualTo("| a |");
    }

    @Test
    void mergePages_cutoffAcrossThreePages_mergesAll() {
        DocumentPage page1 = pageWith(chunk("text", "a", null, true));
        DocumentPage page2 = pageWith(chunk("text", "b", null, true));
        DocumentPage page3 = pageWith(chunk("text", "c", null, false));

        var result = chunker.mergePages(List.of(entry(1, page1), entry(2, page2), entry(3, page3)));

        assertThat(result.get(0).getChunks().getFirst().getContent()).isEqualTo("a b c");
        assertThat(result.get(0).getChunks().getFirst().getContinuesOnNextPage()).isFalse();
        assertThat(result.get(1).getChunks()).isEmpty();
        assertThat(result.get(2).getChunks()).isEmpty();
    }
    
    @Test
    void mergePages_cutoffNotMergedIfNonContiguosPages() {
        DocumentPage page1 = pageWith(chunk("text", "a", null, true));
        DocumentPage page2 = pageWith(chunk("text", "b", null, true));
        DocumentPage page4 = pageWith(chunk("text", "c", null, false));

        var result = chunker.mergePages(List.of(entry(1, page1), entry(2, page2), entry(4, page4)));

        assertThat(result.get(0).getChunks().getFirst().getContent()).isEqualTo("a b");
        assertThat(result.get(0).getChunks().getFirst().getContinuesOnNextPage()).isTrue();
        assertThat(result.get(1).getChunks()).isEmpty();
        assertThat(result.get(2).getChunks()).containsExactlyElementsOf(page4.getChunks());
    }

    @Test
    void mergePages_tableCutoff_joinsSummaries() {
        DocumentPage page1 = pageWith(chunk("table", "| row1 |", "first half", true));
        DocumentPage page2 = pageWith(chunk("table", "| row2 |", "second half", false));

        var result = chunker.mergePages(List.of(entry(1, page1), entry(2, page2)));

        Chunk merged = result.getFirst().getChunks().getFirst();
        assertThat(merged.getContent()).isEqualTo("| row1 | | row2 |");
        assertThat(merged.getSummary()).isEqualTo("first half second half");
    }

    @Test
    void mergePages_nullOrEmptyPagesAreSkipped() {
        DocumentPage page1 = pageWith(chunk("text", "x", null, false));
        DocumentPage nullChunks = new DocumentPage(null);

        var result = chunker.mergePages(new ArrayList<>(List.of(entry(1, page1), entry(2, nullChunks))));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getChunks().getFirst().getContent()).isEqualTo("x");
    }

    private static Chunk chunk(String type, String content, String summary, boolean continues) {
        return new Chunk(ChunkType.valueOf(type), content, summary, continues);
    }

    private static DocumentPage pageWith(Chunk... chunks) {
        return new DocumentPage(new ArrayList<>(List.of(chunks)));
    }
}
