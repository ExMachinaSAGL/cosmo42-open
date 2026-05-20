package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import ch.exmachina.cosmo42.testsupport.ChatModelMocks;
import ch.exmachina.cosmo42.testsupport.SyncExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KBDocumentChunkerTest {

    FileConverter fileConverter;
    ChatModel chatModel;
    KBDocumentChunker chunker;

    @BeforeEach
    void setUp() {
        fileConverter = mock(FileConverter.class);
        chatModel = ChatModelMocks.replyingWith("dummy");
        chunker = new KBDocumentChunker(
                fileConverter,
                chatModel,
                OpenAiChatOptions.builder().model("test-model").temperature(0.1),
                SyncExecutor.newInstance());
    }

    @Nested
    class MergeCrossPageCutoffs {

        @Test
        void emptyInputReturnsEmptyList() {
            assertThat(chunker.mergeCrossPageCutoffs(new ArrayList<>())).isEmpty();
        }

        @Test
        void singlePageWithNoCutoffReturnedUnchanged() {
            DocumentPage p = page(text("hello", false));

            chunker.mergeCrossPageCutoffs(new ArrayList<>(List.of(p)));

            assertThat(p.getChunks()).hasSize(1);
            assertThat(p.getChunks().get(0).getContent()).isEqualTo("hello");
        }

        @Test
        void lastChunkContinuesButNoNextPageStaysAsIs() {
            // Pending is set but never consumed; chunk's continues=true is left on it.
            DocumentPage p = page(text("dangling", true));

            chunker.mergeCrossPageCutoffs(new ArrayList<>(List.of(p)));

            assertThat(p.getChunks()).hasSize(1);
            assertThat(p.getChunks().get(0).getContent()).isEqualTo("dangling");
            assertThat(p.getChunks().get(0).getContinuesOnNextPage()).isTrue();
        }

        @Test
        void matchingTypeAcrossPageBreakMergesAndRemovesFirstOfNextPage() {
            Chunk cutoff = text("first part", true);
            DocumentPage p1 = page(cutoff);
            DocumentPage p2 = page(text("second part", false), text("unrelated", false));

            chunker.mergeCrossPageCutoffs(new ArrayList<>(List.of(p1, p2)));

            assertThat(p1.getChunks().get(0).getContent()).isEqualTo("first part second part");
            assertThat(p1.getChunks().get(0).getContinuesOnNextPage()).isFalse();
            assertThat(p2.getChunks()).hasSize(1);
            assertThat(p2.getChunks().get(0).getContent()).isEqualTo("unrelated");
        }

        @Test
        void typeMismatchAtPageBoundaryProducesNoMerge() {
            DocumentPage p1 = page(text("text continues", true));
            DocumentPage p2 = page(table("| t |", "table summary", false));

            chunker.mergeCrossPageCutoffs(new ArrayList<>(List.of(p1, p2)));

            assertThat(p1.getChunks().get(0).getContent()).isEqualTo("text continues");
            assertThat(p2.getChunks().get(0).getContent()).isEqualTo("| t |");
        }

        @Test
        void tripleCutoffMergesAcrossThreePages() {
            Chunk cutoff = text("part one", true);
            DocumentPage p1 = page(cutoff);
            DocumentPage p2 = page(text("part two", true));
            DocumentPage p3 = page(text("part three", false));

            chunker.mergeCrossPageCutoffs(new ArrayList<>(List.of(p1, p2, p3)));

            assertThat(p1.getChunks().get(0).getContent()).isEqualTo("part one part two part three");
            assertThat(p1.getChunks().get(0).getContinuesOnNextPage()).isFalse();
            assertThat(p2.getChunks()).isEmpty();
            assertThat(p3.getChunks()).isEmpty();
        }

        @Test
        void pendingClearedWhenMergeProducesContinuesFalse() {
            Chunk cutoff = text("first", true);
            DocumentPage p1 = page(cutoff);
            DocumentPage p2 = page(text("second", false));
            DocumentPage p3 = page(text("third standalone", false));

            chunker.mergeCrossPageCutoffs(new ArrayList<>(List.of(p1, p2, p3)));

            assertThat(p1.getChunks().get(0).getContent()).isEqualTo("first second");
            assertThat(p3.getChunks().get(0).getContent()).isEqualTo("third standalone");
        }

        @Test
        void summaryMergingConcatenatesWhenBothPresent() {
            Chunk cutoff = table("| A |", "summary one", true);
            DocumentPage p1 = page(cutoff);
            DocumentPage p2 = page(table("| B |", "summary two", false));

            chunker.mergeCrossPageCutoffs(new ArrayList<>(List.of(p1, p2)));

            assertThat(p1.getChunks().get(0).getSummary()).isEqualTo("summary one summary two");
        }

        @Test
        void summaryFromNewChunkUsedWhenPendingHasNone() {
            Chunk cutoff = textNoSummary("AAA", true);
            DocumentPage p1 = page(cutoff);
            DocumentPage p2 = page(table("BBB", "new summary", false));
            // Type mismatch text vs table — no merge happens. But what if pending is text with no summary
            // and new is text with summary? Let's craft that.
            // (This test variant: same type, pending has no summary, new has summary.)
            Chunk cutoff2 = text("AAA", true);
            DocumentPage p3 = page(cutoff2);
            Chunk withSummary = new Chunk();
            withSummary.setType("text");
            withSummary.setContent("BBB");
            withSummary.setSummary("inherited summary");
            withSummary.setContinuesOnNextPage(false);
            DocumentPage p4 = page(withSummary);

            chunker.mergeCrossPageCutoffs(new ArrayList<>(List.of(p3, p4)));

            assertThat(cutoff2.getSummary()).isEqualTo("inherited summary");
        }

        @Test
        void pendingSummaryRetainedWhenNewChunkHasNone() {
            Chunk cutoff = table("| A |", "kept summary", true);
            DocumentPage p1 = page(cutoff);
            Chunk nextNoSummary = new Chunk();
            nextNoSummary.setType("table");
            nextNoSummary.setContent("| B |");
            nextNoSummary.setSummary(null);
            nextNoSummary.setContinuesOnNextPage(false);
            DocumentPage p2 = page(nextNoSummary);

            chunker.mergeCrossPageCutoffs(new ArrayList<>(List.of(p1, p2)));

            assertThat(cutoff.getSummary()).isEqualTo("kept summary");
        }

        @Test
        void lastChunkWithContinuesTrueTracksAsPendingForNextPage() {
            // First chunk does NOT continue (no pending after it),
            // second chunk continues (pending after page).
            DocumentPage p1 = page(text("standalone", false), text("dangling", true));
            DocumentPage p2 = page(text("continued", false));

            chunker.mergeCrossPageCutoffs(new ArrayList<>(List.of(p1, p2)));

            assertThat(p1.getChunks().get(1).getContent()).isEqualTo("dangling continued");
            assertThat(p2.getChunks()).isEmpty();
        }
    }

    @Nested
    class ExtractRawChunks {

        @Test
        void chunksEachPageInOrderUsingTheInjectedExecutor() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "x.pdf", "application/pdf", new byte[]{1});
            when(fileConverter.convertSupportedFileToPdf(file)).thenReturn(new byte[]{1});
            when(fileConverter.convertPdfToImages(any())).thenReturn(List.of(
                    new byte[]{10}, new byte[]{20}));

            // Each call returns a different page worth of JSON.
            Iterator<String> pageJsons = List.of(
                    pageJson(chunkJson("text", "page-1 content", null, false)),
                    pageJson(chunkJson("text", "page-2 content", null, false))
            ).iterator();
            when(chatModel.call(any(Prompt.class))).thenAnswer(invocation ->
                    new ChatResponse(List.of(new Generation(
                            new AssistantMessage(pageJsons.next())))));

            List<DocumentPage> result = chunker.extractRawChunks(file);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getChunks().get(0).getContent()).isEqualTo("page-1 content");
            assertThat(result.get(1).getChunks().get(0).getContent()).isEqualTo("page-2 content");
            verify(fileConverter).convertSupportedFileToPdf(file);
        }

        @Test
        void llmExceptionOnOnePageSkipsThatPageButKeepsOthers() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "x.pdf", "application/pdf", new byte[]{1});
            when(fileConverter.convertSupportedFileToPdf(file)).thenReturn(new byte[]{1});
            when(fileConverter.convertPdfToImages(any())).thenReturn(List.of(
                    new byte[]{10}, new byte[]{20}));

            Iterator<Object> responses = List.<Object>of(
                    new RuntimeException("page 1 boom"),
                    pageJson(chunkJson("text", "survivor", null, false))
            ).iterator();
            when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
                Object next = responses.next();
                if (next instanceof RuntimeException re) {
                    throw re;
                }
                return new ChatResponse(List.of(new Generation(new AssistantMessage((String) next))));
            });

            List<DocumentPage> result = chunker.extractRawChunks(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getChunks().get(0).getContent()).isEqualTo("survivor");
        }

        @Test
        void crossPageCutoffMergesWhenLastChunkOfPageOneContinues() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "x.pdf", "application/pdf", new byte[]{1});
            when(fileConverter.convertSupportedFileToPdf(file)).thenReturn(new byte[]{1});
            when(fileConverter.convertPdfToImages(any())).thenReturn(List.of(
                    new byte[]{10}, new byte[]{20}));

            Iterator<String> pageJsons = List.of(
                    pageJson(chunkJson("text", "front", null, true)),
                    pageJson(chunkJson("text", "back", null, false))
            ).iterator();
            when(chatModel.call(any(Prompt.class))).thenAnswer(invocation ->
                    new ChatResponse(List.of(new Generation(
                            new AssistantMessage(pageJsons.next())))));

            List<DocumentPage> result = chunker.extractRawChunks(file);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getChunks().get(0).getContent()).isEqualTo("front back");
            assertThat(result.get(1).getChunks()).isEmpty();
        }
    }

    private static DocumentPage page(Chunk... chunks) {
        DocumentPage p = new DocumentPage();
        p.setChunks(new ArrayList<>(List.of(chunks)));
        return p;
    }

    private static Chunk text(String content, boolean continues) {
        Chunk c = new Chunk();
        c.setType("text");
        c.setContent(content);
        c.setSummary(null);
        c.setContinuesOnNextPage(continues);
        return c;
    }

    private static Chunk textNoSummary(String content, boolean continues) {
        return text(content, continues);
    }

    private static Chunk table(String content, String summary, boolean continues) {
        Chunk c = new Chunk();
        c.setType("table");
        c.setContent(content);
        c.setSummary(summary);
        c.setContinuesOnNextPage(continues);
        return c;
    }

    private static String chunkJson(String type, String content, String summary, boolean continues) {
        String summaryField = summary == null ? "null" : "\"" + summary + "\"";
        return "{\"type\":\"" + type + "\",\"content\":\"" + content
                + "\",\"summary\":" + summaryField
                + ",\"continuesOnNextPage\":" + continues + "}";
    }

    private static String pageJson(String... chunkJsons) {
        return "{\"chunks\":[" + String.join(",", chunkJsons) + "]}";
    }
}
