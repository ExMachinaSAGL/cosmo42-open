package ch.exmachina.cosmo42.services.chat.processors;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.chat.ChatContext;
import ch.exmachina.cosmo42.services.chat.tools.KBDocumentSimilaritySearchTool;
import ch.exmachina.cosmo42.services.kb.MarkdownLinkProcessor;
import ch.exmachina.cosmo42.testsupport.ChatModelMocks;
import ch.exmachina.cosmo42.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationProcessorTest {

    ChatModel chatModel;
    ChatMemory chatMemory;
    KBDocumentSimilaritySearchTool tool;
    KBDocumentRepository kbDocumentRepository;
    ConversationProcessor processor;
    KBDocumentRepository kbDocumentRepository;
    MarkdownLinkProcessor markdownLinkProcessor;

    @BeforeEach
    void setUp() {
        chatModel = ChatModelMocks.replyingWith("dummy");
        chatMemory = mock(ChatMemory.class);
        tool = mock(KBDocumentSimilaritySearchTool.class);
        kbDocumentRepository = mock(KBDocumentRepository.class);
        when(chatMemory.get(any())).thenReturn(List.of());
        when(kbDocumentRepository.findAll()).thenReturn(List.of());
        markdownLinkProcessor = new MarkdownLinkProcessor();
        processor = new ConversationProcessor(
                chatModel,
                OpenAiChatOptions.builder().model("test-model").temperature(0.2),
                chatMemory,
                tool,
                kbDocumentRepository,
                markdownLinkProcessor
        );
    }

    @Nested
    class Contract {

        @Test
        void promptCarriesSystemInstructionDefiningCosmo42Persona() {
            when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response("ok\n")));
            ChatContext ctx = newContext("u-1", "what is X?");

            processor.process(ctx).blockLast();

            ArgumentCaptor<Prompt> cap = ArgumentCaptor.forClass(Prompt.class);
            verify(chatModel).stream(cap.capture());
            String systemText = cap.getValue().getSystemMessage().getText();
            assertThat(systemText).contains("You are cosmo42");
            assertThat(systemText).contains("private knowledge base");
            assertThat(systemText).contains("REF_FILE_");
            assertThat(systemText).contains("SEARCH FLOW (RAG)");
        }

        @Test
        void promptCarriesTheUserMessageVerbatim() {
            when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response("ok\n")));
            ChatContext ctx = newContext("u-1", "Tell me about the Q3 report.");

            processor.process(ctx).blockLast();

            ArgumentCaptor<Prompt> cap = ArgumentCaptor.forClass(Prompt.class);
            verify(chatModel).stream(cap.capture());
            assertThat(cap.getValue().getUserMessage().getText())
                    .isEqualTo("Tell me about the Q3 report.");
        }

        @Test
        void streamBuffersChunksUntilNewlineThenEmitsOneSseEventPerLine() {
            when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                    response("hello "),
                    response("world\n"),
                    response("second line\n"),
                    response("tail no newline")));
            ChatContext ctx = newContext("u-1", "hi");

            StepVerifier.create(processor.process(ctx))
                    .assertNext(sse -> assertChunkEvent(sse, "hello world\n"))
                    .assertNext(sse -> assertChunkEvent(sse, "second line\n"))
                    .assertNext(sse -> assertChunkEvent(sse, "tail no newline"))
                    .verifyComplete();
        }

        @Test
        void responseWithEmptyTextStillEmitsExactlyOneSseEvent() {
            ChatResponse withEmptyText = new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
            when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(withEmptyText));
            ChatContext ctx = newContext("u-1", "hi");

            StepVerifier.create(processor.process(ctx))
                    .assertNext(sse -> assertChunkEvent(sse, ""))
                    .verifyComplete();
        }

        @Test
        void responseWithNullResultMappedToEmptyChunkData() {
            ChatResponse withNullResult = new ChatResponse(List.of());
            when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(withNullResult));
            ChatContext ctx = newContext("u-1", "hi");

            StepVerifier.create(processor.process(ctx))
                    .assertNext(sse -> assertChunkEvent(sse, ""))
                    .verifyComplete();
        }

        @Test
        void conversationIdParameterPropagatedToChatMemoryAdvisor() {
            when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response("ok\n")));
            ChatContext ctx = newContext("conv-xyz", "hi");

            processor.process(ctx).blockLast();

            verify(chatMemory).get("conv-xyz");
        }

        @Test
        void referenceTokensInLlmOutputAreRewrittenToMarkdownLinks() {
            String uuid = "1d52d4f1-1c5b-4be8-8b1c-0123456789ab";
            KBDocument doc = Fixtures.document(uuid, "report.pdf");
            when(kbDocumentRepository.findAll()).thenReturn(List.of(doc));
            when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                    response("see REF_FILE_" + uuid + "\n")));
            ChatContext ctx = newContext("u-1", "where is it?");

            StepVerifier.create(processor.process(ctx))
                    .assertNext(sse -> {
                        assertThat(sse.data()).isNotNull();
                        assertThat(sse.data().getType()).isEqualTo(ChatEventType.CHUNK);
                        assertThat((String) sse.data().getData())
                                .contains("(/api/v1/kb/documents/" + uuid + "/download)")
                                .doesNotContain("REF_FILE_");
                    })
                    .verifyComplete();
        }

        @Test
        void unknownReferenceTokensAreStrippedFromOutput() {
            when(kbDocumentRepository.findAll()).thenReturn(List.of());
            String fakeUuid = "ffffffff-ffff-ffff-ffff-ffffffffffff";
            when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                    response("text REF_FILE_" + fakeUuid + " more\n")));
            ChatContext ctx = newContext("u-1", "hi");

            StepVerifier.create(processor.process(ctx))
                    .assertNext(sse -> assertThat((String) Objects.requireNonNull(sse.data()).getData())
                            .doesNotContain("REF_FILE_")
                            .doesNotContain(fakeUuid))
                    .verifyComplete();
        }
    }

    @Nested
    class MemoryAdvisor {

        @BeforeEach
        void stubChatMemoryForU1() {
            when(chatMemory.get("u-1")).thenReturn(List.of());
        }

        @Test
        void doesNotManuallyPersistMessagesBecauseMemoryAdvisorOwnsPersistence() {
            when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(
                    List.of(new Generation(new AssistantMessage("answer\n"))))));

            ChatContext context = ChatContext.builder()
                    .newChat(false)
                    .chatUuid("u-1")
                    .request(new ChatRequestDTO("u-1", "hello"))
                    .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                    .build();

            StepVerifier.create(processor.process(context))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(chatMemory, never()).add(eq("u-1"), argThat((List<Message> messages) -> messages.size() == 2));
        }
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatContext newContext(String uuid, String message) {
        return ChatContext.builder()
                .newChat(false)
                .chatUuid(uuid)
                .request(new ChatRequestDTO(uuid, message))
                .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                .build();
    }

    private static void assertChunkEvent(ServerSentEvent<ChatResponseDTO> sse, String expectedText) {
        assertThat(sse.data()).isNotNull();
        assertThat(sse.data().getType()).isEqualTo(ChatEventType.CHUNK);
        assertThat(sse.data().getData()).isEqualTo(expectedText);
    }
}
