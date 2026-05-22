package ch.exmachina.cosmo42.services.chat.processors;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.ChatContext;
import ch.exmachina.cosmo42.services.chat.ChatConversationService;
import ch.exmachina.cosmo42.testsupport.ChatModelMocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class TitleProcessorTest {

    ChatModel chatModel;
    OpenAiChatOptions.Builder titleOptionsBuilder;
    ChatConversationService conversationService;
    ch.exmachina.cosmo42.services.chat.TitleSanitizer titleSanitizer;
    TitleProcessor processor;

    @BeforeEach
    void setUp() {
        chatModel = ChatModelMocks.replyingWith("dummy title");
        titleOptionsBuilder = OpenAiChatOptions.builder().model("test-model").maxTokens(32);
        conversationService = mock(ChatConversationService.class);
        titleSanitizer = new ch.exmachina.cosmo42.services.chat.TitleSanitizer();
        processor = new TitleProcessor(chatModel, titleOptionsBuilder, conversationService, titleSanitizer);
    }

    @Test
    void existingChatProducesEmptyFlux() {
        ChatContext ctx = ChatContext.builder()
                .newChat(false)
                .chatUuid("u-1")
                .request(new ChatRequestDTO("u-1", "hello"))
                .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                .build();

        StepVerifier.create(processor.process(ctx))
                .verifyComplete();

        verifyNoInteractions(chatModel, conversationService);
    }

    @Test
    void newChatPersistsTitleAndEmitsSseEvent() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                java.util.List.of(new Generation(new AssistantMessage("My Title")))));

        ChatContext ctx = ChatContext.builder()
                .newChat(true)
                .chatUuid("u-1")
                .request(new ChatRequestDTO(null, "How do I deploy?"))
                .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                .build();

        StepVerifier.create(processor.process(ctx))
                .assertNext(sse -> {
                    assert sse.data() != null;
                    assertThat(sse.data().getType()).isEqualTo(ChatEventType.TITLE);
                    assertThat(sse.data().getData()).isEqualTo("My Title");
                })
                .verifyComplete();

        verify(conversationService).persistGeneratedTitle("u-1", "My Title");
    }

    @Test
    void promptIncludesSystemInstructionAndUserMessage() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                java.util.List.of(new Generation(new AssistantMessage("My Title")))));

        ChatContext ctx = ChatContext.builder()
                .newChat(true)
                .chatUuid("u-1")
                .request(new ChatRequestDTO(null, "How do I deploy?"))
                .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                .build();

        StepVerifier.create(processor.process(ctx)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Prompt> cap = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(cap.capture());
        Prompt prompt = cap.getValue();
        assertThat(prompt.getSystemMessage().getText())
                .contains("concise conversation title")
                .contains("max 5 words");
        assertThat(prompt.getUserMessage().getText()).isEqualTo("How do I deploy?");
    }

    @Test
    void newChatEmitsStatusEventBeforeLlmCall() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                java.util.List.of(new Generation(new AssistantMessage("My Title")))));

        Sinks.Many<ServerSentEvent<ChatResponseDTO>> sink =
                Sinks.many().multicast().onBackpressureBuffer();
        ChatContext ctx = ChatContext.builder()
                .newChat(true)
                .chatUuid("u-1")
                .request(new ChatRequestDTO(null, "q"))
                .eventSink(sink)
                .build();

        processor.process(ctx).blockLast();
        sink.tryEmitComplete();

        StepVerifier.create(sink.asFlux())
                .assertNext(sse -> {
                    assert sse.data() != null;
                    assertThat(sse.data().getType()).isEqualTo(ChatEventType.STATUS);
                    assertThat(sse.data().getData()).isEqualTo("Generating Chat Title...");
                })
                .verifyComplete();
    }

    @Test
    void existingChatDoesNotEmitStatusEvent() {
        Sinks.Many<ServerSentEvent<ChatResponseDTO>> sink =
                Sinks.many().multicast().onBackpressureBuffer();
        ChatContext ctx = ChatContext.builder()
                .newChat(false)
                .chatUuid("u-1")
                .request(new ChatRequestDTO("u-1", "follow-up"))
                .eventSink(sink)
                .build();

        processor.process(ctx).blockLast();
        sink.tryEmitComplete();

        StepVerifier.create(sink.asFlux()).verifyComplete();
    }

    @Test
    void newChatLogsTitleGenerationLifecycle(CapturedOutput output) {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                java.util.List.of(new Generation(new AssistantMessage("My Title")))));

        ChatContext ctx = ChatContext.builder()
                .newChat(true)
                .chatUuid("u-logs")
                .request(new ChatRequestDTO(null, "How do I deploy?"))
                .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                .build();

        StepVerifier.create(processor.process(ctx))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(output)
                .contains("Title generation requested uuid=u-logs")
                .contains("Title generation succeeded uuid=u-logs")
                .contains("titleLength=8");
    }

    @Test
    void newChatEmitsSanitizedTitle() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                java.util.List.of(new Generation(new AssistantMessage("Title: \"Quoted\"\nextra")))));

        ChatContext ctx = ChatContext.builder()
                .newChat(true)
                .chatUuid("u-2")
                .request(new ChatRequestDTO(null, "q"))
                .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                .build();

        StepVerifier.create(processor.process(ctx))
                .assertNext(sse -> {
                    assert sse.data() != null;
                    assertThat(sse.data().getData()).isEqualTo("Quoted");
                })
                .verifyComplete();

        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(conversationService).persistGeneratedTitle(eq("u-2"), raw.capture());
        assertThat(raw.getValue()).isEqualTo("Quoted");
    }

    @Test
    void newChatSkipsTitleEventWhenSanitizedTitleIsBlank() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                java.util.List.of(new Generation(new AssistantMessage("   ")))));

        ChatContext ctx = ChatContext.builder()
                .newChat(true)
                .chatUuid("u-blank")
                .request(new ChatRequestDTO(null, "q"))
                .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                .build();

        StepVerifier.create(processor.process(ctx))
                .expectNextCount(1)
                .verifyComplete();

        verify(conversationService).persistGeneratedTitle(eq("u-blank"), startsWith("Chat "));
    }

    @Test
    void llmFailureSwallowedNoTitleNoPersistence() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("upstream"));

        ChatContext ctx = ChatContext.builder()
                .newChat(true)
                .chatUuid("u-3")
                .request(new ChatRequestDTO(null, "q"))
                .eventSink(Sinks.many().multicast().onBackpressureBuffer())
                .build();

        StepVerifier.create(processor.process(ctx)).verifyComplete();

        verify(conversationService, never()).persistGeneratedTitle(any(), any());
    }
}
