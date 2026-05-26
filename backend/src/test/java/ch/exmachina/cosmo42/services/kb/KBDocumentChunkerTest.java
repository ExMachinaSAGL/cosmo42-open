package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import ch.exmachina.cosmo42.testsupport.ChatModelMocks;
import ch.exmachina.cosmo42.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KBDocumentChunkerTest {

    private static final byte[] PAGE_0 = new byte[]{1, 2, 3};
    private static final byte[] PAGE_1 = new byte[]{4, 5, 6};
    private static final byte[] PAGE_2 = new byte[]{7, 8, 9};

    private ChatModel chatModel;
    private KBDocumentChunker chunker;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        ChatModelMocks.stubDefaultOptions(chatModel);
        chunker = new KBDocumentChunker(chatModel, OpenAiChatOptions.builder(), 1, 600);
    }

    @Test
    void processPages_allIndices_callsCallbackForEachPage() {
        stubStreamResponse(pageJson("Page 0 content"));
        ConcurrentMap<Integer, DocumentPage> results = new ConcurrentHashMap<>();

        chunker.processPages(List.of(PAGE_0, PAGE_1), null, results::put);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getChunks()).isNotEmpty();
        assertThat(results.get(0).getChunks().getFirst().getContent()).isEqualTo("Page 0 content");
        assertThat(results.get(1).getChunks()).isNotEmpty();
        assertThat(results.get(1).getChunks().getFirst().getContent()).isEqualTo("Page 0 content");
    }

    @Test
    void processPages_specificIndices_processesOnlyThose() {
        stubStreamResponse(pageJson("content"));
        ConcurrentMap<Integer, DocumentPage> results = new ConcurrentHashMap<>();

        chunker.processPages(List.of(PAGE_0, PAGE_1, PAGE_2), Set.of(0, 2), results::put);

        assertThat(results).hasSize(2);
        assertThat(results).containsKeys(0, 2);
        assertThat(results).doesNotContainKey(1);
    }

    @Test
    void processPages_nullIndices_processesAll() {
        stubStreamResponse(pageJson("x"));
        ConcurrentMap<Integer, DocumentPage> results = new ConcurrentHashMap<>();

        chunker.processPages(List.of(PAGE_0, PAGE_1, PAGE_2), null, results::put);

        assertThat(results).hasSize(3);
        assertThat(results).containsKeys(0, 1, 2);
    }

    @Test
    void processPages_emptyIndices_processesNone() {
        stubStreamResponse(pageJson("x"));
        ConcurrentMap<Integer, DocumentPage> results = new ConcurrentHashMap<>();

        chunker.processPages(List.of(PAGE_0, PAGE_1), new LinkedHashSet<>(), results::put);

        assertThat(results).isEmpty();
    }

    @Test
    void processPages_outOfRangeIndices_skipped() {
        stubStreamResponse(pageJson("x"));
        ConcurrentMap<Integer, DocumentPage> results = new ConcurrentHashMap<>();

        chunker.processPages(List.of(PAGE_0), Set.of(0, 5, -1), results::put);

        assertThat(results).hasSize(1);
        assertThat(results).containsKey(0);
    }

    @Test
    void processPages_emptyResponse_returnsNullPage() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(""))))));
        ConcurrentMap<Integer, DocumentPage> results = new ConcurrentHashMap<>();

        chunker.processPages(List.of(PAGE_0), null, results::put);

        assertThat(results.get(0)).isNull();
    }

    @Test
    void processPages_blankResponse_returnsNullPage() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("   "))))));
        ConcurrentMap<Integer, DocumentPage> results = new ConcurrentHashMap<>();

        chunker.processPages(List.of(PAGE_0), null, results::put);

        assertThat(results.get(0)).isNull();
    }

    @Test
    void processPages_llmError_returnsNullDocumentPage() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("upstream failure")));
        ConcurrentMap<Integer, DocumentPage> results = new ConcurrentHashMap<>();

        chunker.processPages(List.of(PAGE_0), null, results::put);

        assertThat(results.get(0)).isNotNull();
        assertThat(results.get(0).getChunks()).isNull();
    }

    @Test
    void processPages_withTableChunk_passesTableThrough() {
        String tableJson = """
                {"chunks":[{"type":"table","content":"| A | B |","summary":"Sales data","continuesOnNextPage":false}]}""";
        stubStreamResponse(tableJson);
        ConcurrentMap<Integer, DocumentPage> results = new ConcurrentHashMap<>();

        chunker.processPages(List.of(PAGE_0), null, results::put);

        DocumentPage page = results.get(0);
        assertThat(page.getChunks()).hasSize(1);
        assertThat(page.getChunks().getFirst().getType()).isEqualTo("table");
        assertThat(page.getChunks().getFirst().getContent()).isEqualTo("| A | B |");
        assertThat(page.getChunks().getFirst().getSummary()).isEqualTo("Sales data");
    }

    @Test
    void processPages_malformedJson_returnsPageWithNullChunks() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("not valid json"))))));
        ConcurrentMap<Integer, DocumentPage> results = new ConcurrentHashMap<>();

        chunker.processPages(List.of(PAGE_0), null, results::put);

        DocumentPage page = results.get(0);
        assertThat(page).isNotNull();
        assertThat(page.getChunks()).isNull();
    }

    @Test
    void mergePages_delegatesCorrectly() {
        DocumentPage page1 = Fixtures.page(Fixtures.textChunk("hello"));
        DocumentPage page2 = Fixtures.page(Fixtures.textChunk("world"));

        List<DocumentPage> result = chunker.mergePages(List.of(page1, page2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getChunks().getFirst().getContent()).isEqualTo("hello");
        assertThat(result.get(1).getChunks().getFirst().getContent()).isEqualTo("world");
    }

    private void stubStreamResponse(String json) {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(json))))));
    }

    private static String pageJson(String content) {
        return "{\"chunks\":[{\"type\":\"text\",\"content\":\"" + content + "\",\"continuesOnNextPage\":false}]}";
    }
}
