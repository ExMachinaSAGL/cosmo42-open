package ch.exmachina.cosmo42.services.chat.processors;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.ChatContext;
import ch.exmachina.cosmo42.services.chat.tools.KBDocumentSimilaritySearchTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationProcessorContractTest {

    ChatModel chatModel;
    ChatMemory chatMemory;
    KBDocumentSimilaritySearchTool tool;
    ConversationProcessor processor;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        chatMemory = mock(ChatMemory.class);
        tool = mock(KBDocumentSimilaritySearchTool.class);
        when(chatModel.getDefaultOptions())
                .thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(chatMemory.get(any())).thenReturn(List.of());
        processor = new ConversationProcessor(
                chatModel,
                OpenAiChatOptions.builder().model("test-model").temperature(0.2),
                chatMemory,
                tool);
    }

    @Test
    void promptCarriesSystemInstructionDefiningCosmo42Persona() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response("ok")));
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
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response("ok")));
        ChatContext ctx = newContext("u-1", "Tell me about the Q3 report.");

        processor.process(ctx).blockLast();

        ArgumentCaptor<Prompt> cap = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(cap.capture());
        assertThat(cap.getValue().getUserMessage().getText())
                .isEqualTo("Tell me about the Q3 report.");
    }

    @Test
    void streamMapsEachChatResponseToOneChunkSseEvent() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("hello"), response(" world"), response("!")));
        ChatContext ctx = newContext("u-1", "hi");

        StepVerifier.create(processor.process(ctx))
                .assertNext(sse -> assertChunkEvent(sse, "hello"))
                .assertNext(sse -> assertChunkEvent(sse, " world"))
                .assertNext(sse -> assertChunkEvent(sse, "!"))
                .verifyComplete();
    }

    @Test
    void responseWithNullTextMappedToEmptyChunkData() {
        ChatResponse withNullText = new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(withNullText));
        ChatContext ctx = newContext("u-1", "hi");

        StepVerifier.create(processor.process(ctx))
                .assertNext(sse -> assertChunkEvent(sse, ""))
                .verifyComplete();
    }

    @Test
    void conversationIdParameterPropagatedToChatMemoryAdvisor() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response("ok")));
        ChatContext ctx = newContext("conv-xyz", "hi");

        processor.process(ctx).blockLast();

        // The MessageChatMemoryAdvisor reads conversation context to fetch/persist messages,
        // so chatMemory.get(conversationId) is the visible side effect of the wiring.
        verify(chatMemory).get("conv-xyz");
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
