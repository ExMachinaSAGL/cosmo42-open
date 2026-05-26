package ch.exmachina.cosmo42.integration;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import ch.exmachina.cosmo42.repositories.ChatConversationRepository;
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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatStreamE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;
    @MockitoBean
    ChatModel chatModel;
    @MockitoBean
    EmbeddingModel embeddingModel;
    @Autowired
    ChatConversationRepository repository;
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
    void newChatStreamsTitleAndPersistsRow() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Deploy Question")))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(
                reactor.core.publisher.Flux.just(new ChatResponse(
                        List.of(new Generation(new AssistantMessage("Some answer.")))
                )));

        client.post().uri("/api/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"uuid\":null,\"message\":\"How do I deploy?\"}")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody()
                .blockLast(Duration.ofSeconds(15));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var all = repository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.getFirst().getTitle()).isEqualTo("Deploy Question");
            assertThat(countMessages(all.getFirst().getUuid(), "USER")).isEqualTo(1);
            assertThat(countMessages(all.getFirst().getUuid(), "ASSISTANT")).isEqualTo(1);
        });
    }

    @Test
    void newChatLlmTitleFailureDoesNotBreakConversation() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("upstream"));
        when(chatModel.stream(any(Prompt.class))).thenReturn(
                reactor.core.publisher.Flux.just(new ChatResponse(
                        List.of(new Generation(new AssistantMessage("answer")))
                )));

        client.post().uri("/api/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"uuid\":null,\"message\":\"q\"}")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody()
                .blockLast(Duration.ofSeconds(15));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var all = repository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.getFirst().getTitle()).isNull();
        });
    }

    private Integer countMessages(String uuid, String type) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ? AND type = ?",
                Integer.class,
                uuid,
                type);
    }
}
