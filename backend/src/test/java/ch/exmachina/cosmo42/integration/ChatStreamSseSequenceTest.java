package ch.exmachina.cosmo42.integration;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.testsupport.ChatModelMocks;
import ch.exmachina.cosmo42.testsupport.TestDbCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatStreamSseSequenceTest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;
    @MockitoBean
    ChatModel chatModel;
    @MockitoBean
    EmbeddingModel embeddingModel;
    @Autowired
    JdbcTemplate jdbcTemplate;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        TestDbCleaner.cleanChatTables(jdbcTemplate);
        ChatModelMocks.stubDefaultOptions(chatModel);
        when(embeddingModel.embed(any(String.class))).thenReturn(new float[1024]);
    }

    @Test
    void newChatStreamYieldsStatusUuidTitleChunksThenCompleted() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Deploy Q")))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("part1 ")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("part2"))))
        ));

        List<ChatEventType> types = collectEventTypes("{\"uuid\":null,\"message\":\"deploy?\"}");

        assertThat(types).first().isEqualTo(ChatEventType.STATUS);
        assertThat(types).last().isEqualTo(ChatEventType.COMPLETED);
        assertThat(types).contains(ChatEventType.UUID, ChatEventType.TITLE, ChatEventType.CHUNK);
        assertOrdered(types, ChatEventType.UUID, ChatEventType.CHUNK);
        assertOrdered(types, ChatEventType.TITLE, ChatEventType.COMPLETED);
        assertThat(types).doesNotContain(ChatEventType.ERROR);
    }

    @Test
    void existingChatStreamEmitsNoUuidAndNoTitle() {
        // Seed an existing conversation so the request points at a known UUID.
        String existingUuid = "11111111-1111-1111-1111-111111111111";
        jdbcTemplate.update(
                "INSERT INTO chat_conversation (uuid, title, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
                existingUuid, "Existing");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("reply"))))
        ));

        List<ChatEventType> types = collectEventTypes(
                "{\"uuid\":\"" + existingUuid + "\",\"message\":\"follow-up\"}");

        assertThat(types).doesNotContain(ChatEventType.UUID);
        assertThat(types).doesNotContain(ChatEventType.TITLE);
        assertThat(types).first().isEqualTo(ChatEventType.STATUS);
        assertThat(types).last().isEqualTo(ChatEventType.COMPLETED);
        assertThat(types).contains(ChatEventType.CHUNK);
    }

    @Test
    void completedAlwaysLastNeverPrecededByChunks() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Title X")))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("a")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("b"))))
        ));

        List<ChatEventType> types = collectEventTypes("{\"uuid\":null,\"message\":\"q\"}");

        int completedAt = types.lastIndexOf(ChatEventType.COMPLETED);
        int lastChunkAt = types.lastIndexOf(ChatEventType.CHUNK);
        assertThat(completedAt).isGreaterThan(lastChunkAt);
        // Only one COMPLETED — no duplicates leaking through the merged sink.
        assertThat(types).filteredOn(t -> t == ChatEventType.COMPLETED).hasSize(1);
    }

    @Test
    void uuidEmittedExactlyOnceForNewChat() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("T")))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))))
        ));

        List<ChatEventType> types = collectEventTypes("{\"uuid\":null,\"message\":\"q\"}");

        assertThat(types).filteredOn(t -> t == ChatEventType.UUID).hasSize(1);
    }

    private List<ChatEventType> collectEventTypes(String body) {
        String raw = client.post().uri("/api/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody()
                .collectList()
                .block(Duration.ofSeconds(15))
                .stream()
                .reduce("", (a, b) -> a + b);
        return parseEventTypes(raw);
    }

    /**
     * Extract each `"type":"X"` from the concatenated SSE payload in source order.
     */
    private static List<ChatEventType> parseEventTypes(String raw) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"type\":\"([A-Z]+)\"").matcher(raw);
        List<ChatEventType> out = new java.util.ArrayList<>();
        while (m.find()) {
            out.add(ChatEventType.valueOf(m.group(1)));
        }
        return out;
    }

    private static void assertOrdered(List<ChatEventType> types, ChatEventType earlier, ChatEventType later) {
        int earlierAt = types.indexOf(earlier);
        int laterAt = types.lastIndexOf(later);
        assertThat(earlierAt)
                .as("expected %s before %s in %s", earlier, later, types)
                .isLessThan(laterAt);
    }
}
