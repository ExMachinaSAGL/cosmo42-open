package ch.exmachina.cosmo42.integration;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import ch.exmachina.cosmo42.entities.ChatConversation;
import ch.exmachina.cosmo42.repositories.ChatConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatConversationCrudE2ETest extends AbstractIntegrationTest {

    @LocalServerPort int port;
    @MockitoBean ChatModel chatModel;
    @MockitoBean EmbeddingModel embeddingModel;
    @Autowired ChatConversationRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(15))
                .build();
        jdbcTemplate.update("DELETE FROM SPRING_AI_CHAT_MEMORY");
        jdbcTemplate.update("DELETE FROM chat_conversation");
        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
    }

    private ChatConversation seed(String uuid, String title, LocalDateTime createdAt) {
        ChatConversation c = new ChatConversation();
        c.setUuid(uuid);
        c.setTitle(title);
        c.setCreatedAt(createdAt);
        c.setUpdatedAt(createdAt);
        return repository.save(c);
    }

    @Test
    void listReturnsMostRecentFirst() {
        seed("u-old", "Old", LocalDateTime.now().minusHours(2));
        seed("u-mid", "Mid", LocalDateTime.now().minusHours(1));
        seed("u-new", "New", LocalDateTime.now());

        client.get().uri("/api/v1/chat?page=0&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].uuid").isEqualTo("u-new")
                .jsonPath("$.content[1].uuid").isEqualTo("u-mid")
                .jsonPath("$.content[2].uuid").isEqualTo("u-old");
    }

    @Test
    void getReturnsConversationWithMessages() {
        String uuid = UUID.randomUUID().toString();
        seed(uuid, "Chat", LocalDateTime.now());
        jdbcTemplate.update(
                "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, timestamp) VALUES (?, ?, ?, ?)",
                uuid, "hi", "USER", LocalDateTime.now());

        client.get().uri("/api/v1/chat/" + uuid)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.uuid").isEqualTo(uuid)
                .jsonPath("$.messages[0].role").isEqualTo("user")
                .jsonPath("$.messages[0].content").isEqualTo("hi");
    }

    @Test
    void patchRenamesTitle() {
        seed("u-1", "Old Title", LocalDateTime.now());

        client.patch().uri("/api/v1/chat/u-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"Brand New Title\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Brand New Title");

        assertThat(repository.findByUuid("u-1").orElseThrow().getTitle()).isEqualTo("Brand New Title");
    }

    @Test
    void deleteCascadesIntoSpringAiChatMemory() {
        String uuid = UUID.randomUUID().toString();
        seed(uuid, "Doomed", LocalDateTime.now());
        jdbcTemplate.update(
                "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, timestamp) VALUES (?, ?, ?, ?)",
                uuid, "m1", "USER", LocalDateTime.now());
        jdbcTemplate.update(
                "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, timestamp) VALUES (?, ?, ?, ?)",
                uuid, "m2", "ASSISTANT", LocalDateTime.now());

        client.delete().uri("/api/v1/chat/" + uuid)
                .exchange()
                .expectStatus().isNoContent();

        assertThat(repository.findByUuid(uuid)).isEmpty();
        Integer leftover = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?",
                Integer.class, uuid);
        assertThat(leftover).isEqualTo(0);
    }

    @Test
    void regenerateUsesFirstUserMessage() {
        String uuid = UUID.randomUUID().toString();
        seed(uuid, null, LocalDateTime.now());
        jdbcTemplate.update(
                "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, timestamp) VALUES (?, ?, ?, ?)",
                uuid, "How do I deploy?", "USER", LocalDateTime.now().minusMinutes(2));
        jdbcTemplate.update(
                "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, timestamp) VALUES (?, ?, ?, ?)",
                uuid, "follow up", "USER", LocalDateTime.now());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Deploy Question")))));

        client.post().uri("/api/v1/chat/" + uuid + "/title:regenerate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Deploy Question");

        assertThat(repository.findByUuid(uuid).orElseThrow().getTitle()).isEqualTo("Deploy Question");
    }

    @Test
    void regenerate404OnUnknownUuid() {
        client.post().uri("/api/v1/chat/missing-uuid/title:regenerate")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void regenerate409OnEmptyHistory() {
        String uuid = UUID.randomUUID().toString();
        seed(uuid, null, LocalDateTime.now());

        client.post().uri("/api/v1/chat/" + uuid + "/title:regenerate")
                .exchange()
                .expectStatus().isEqualTo(409);
    }
}
