package ch.exmachina.cosmo42.services.chat.processors;

import static ch.exmachina.cosmo42.services.chat.ChatAttribute.CITATIONS;
import static ch.exmachina.cosmo42.testsupport.ChatModelMocks.stubDefaultOptions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.http.codec.ServerSentEvent;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.dto.CitationEntryDTO;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.entities.converters.VectorAttributeConverter;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.services.chat.ChatAttribute;
import ch.exmachina.cosmo42.services.chat.ChatContext;
import ch.exmachina.cosmo42.services.chat.tools.KBDocumentSimilaritySearchTool;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ConversationProcessorTest {

    @Mock
    ChatModel chatModel;    
    @Mock
    ChatMemory chatMemory;
    @Mock
    EmbeddingModel embeddingModel;
    @Mock
    OpenAiEmbeddingOptions embeddingModelOptions;
    @Mock
    VectorAttributeConverter vectorAttributeConverter;
    @Mock
    KBDocumentChunkRepository kbDocumentChunkRepository;

    ConversationProcessor processor;

    KBDocumentSimilaritySearchTool tool;
    
    @BeforeEach
    void setUp() {
        stubDefaultOptions(chatModel);
        tool = new KBDocumentSimilaritySearchTool(embeddingModel, embeddingModelOptions, vectorAttributeConverter,
                kbDocumentChunkRepository);
        when(chatMemory.get(any())).thenReturn(List.of());
        processor = new ConversationProcessor(
                chatModel,
                OpenAiChatOptions.builder().model("test-model").temperature(0.2),
                chatMemory,
                tool);
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
            assertThat(systemText).contains("__CITE_");
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
        void citationCollectionAndMemorization() {
            var store = mockMemory(chatMemory);

            var chunk = new KBDocumentChunk();
            chunk.setKbDocument(new KBDocument());
            chunk.getKbDocument().setUuid("uuid");
            chunk.getKbDocument().setFileName("test.pdf");
            chunk.setStartPage(0);
            chunk.setEndPage(2);

            var citation = CitationEntryDTO.builder()
                    .id(1).originalIndex(1).fileName("test.pdf").uuid("uuid").sourcePages(new int[] { 1, 2, 3 })
                    .build();

            var searchToolCall = search("test", chunk);
            when(chatModel.stream(any(Prompt.class)))
                    .thenReturn(Flux.just(searchToolCall))
                    .thenReturn(Flux.just(response("see __CITE_1__\n")));

            ChatContext ctx = newContext("u-1", "where is it?\n");

            StepVerifier.create(Flux.merge(ctx.getEventSink().asFlux(), processor.process(ctx)))
                    .assertNext(sse -> {
                        assertThat(sse.data()).isNotNull();
                        assertThat(sse.data().getType()).isEqualTo(ChatEventType.STATUS);
                        assertThat(sse.data().getData()).isEqualTo("Searching Knowledge Base...");
                    })
                    .assertNext(sse -> {
                        assertThat(sse.data()).isNotNull();
                        assertThat(sse.data().getType()).isEqualTo(ChatEventType.CHUNK);
                        assertThat((String) sse.data().getData()).contains("__CITE_1__");
                    })
                    .assertNext(sse -> {
                        assertThat(sse.data()).isNotNull();
                        assertThat(sse.data().getType()).isEqualTo(ChatEventType.CITATIONS);
                        assertThat((List) sse.data().getData())
                                .usingRecursiveFieldByFieldElementComparator()
                                .containsExactly(citation);

                        ctx.getEventSink().tryEmitComplete();
                    })
                    .verifyComplete();

            assertThat(store).hasSize(1)
                    .allSatisfy((_, conversation) -> {
                        assertThat(conversation).hasSize(2)
                                .element(1).satisfies(message -> {
                                    assertThat((List) message.getMetadata().get(ChatAttribute.CITATIONS.name()))
                                            .usingRecursiveFieldByFieldElementComparator()
                                            .containsExactly(citation);
                                });
                    });
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

    private static ChatResponse response(String text, CitationEntryDTO... citations) {
        var assistantMessage = new AssistantMessage(text);
        if (citations != null && citations.length > 0) {
            assistantMessage.getMetadata().put(CITATIONS.name(), List.of(citations));
        }
        return ChatResponse.builder()
                .generations(List.of(new Generation(
                        assistantMessage,
                        ChatGenerationMetadata.builder().finishReason("EOS").build())))
                .build();
    }

    private ChatResponse search(String query, KBDocumentChunk... chunks) {
        var toolCall = new AssistantMessage.ToolCall(
                "id",
                "function",
                "search",
                "{\"query\": \"test\"}");

        when(embeddingModel.call(any())).thenReturn(new EmbeddingResponse(List.of(new Embedding(null, null))));

        when(kbDocumentChunkRepository.findMostSimilarByCosine(any(), any(), anyInt())).thenReturn(List.of(chunks));

        var assistantMessage = AssistantMessage.builder().toolCalls(List.of(toolCall)).build();
        return ChatResponse.builder()
                .generations((List.of(new Generation(assistantMessage))))
                .build();
    }

    private static Map<String, List<Message>> mockMemory(ChatMemory chatMemory) {
        var memory = new HashMap<String, List<Message>>();
        doAnswer(i -> {
            memory.computeIfAbsent(i.getArgument(0), _ -> new ArrayList<Message>())
                    .addAll(i.getArgument(1));
            return null;
        }).when(chatMemory).add(any(), Mockito.<List<Message>>any());

        doCallRealMethod().when(chatMemory).add(any(), Mockito.<Message>any());

        when(chatMemory.get(any())).thenAnswer(i -> memory.getOrDefault(i.getArgument(0), List.<Message>of()));
        return memory;
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
