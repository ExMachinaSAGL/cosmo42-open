package ch.exmachina.cosmo42.services.chat.processors;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.services.chat.ChatContext;
import ch.exmachina.cosmo42.services.chat.ChatConversationService;
import ch.exmachina.cosmo42.services.chat.TitleGeneratorAdvisor;
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
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class TitleProcessorTest {

    ChatModel chatModel;
    OpenAiChatOptions.Builder titleOptionsBuilder;
    TitleGeneratorAdvisor advisor;
    ChatConversationService conversationService;
    ch.exmachina.cosmo42.services.chat.TitleSanitizer titleSanitizer;
    TitleProcessor processor;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        titleOptionsBuilder = OpenAiChatOptions.builder().model("test-model").maxTokens(32);
        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        advisor = new TitleGeneratorAdvisor();
        conversationService = mock(ChatConversationService.class);
        titleSanitizer = new ch.exmachina.cosmo42.services.chat.TitleSanitizer();
        processor = new TitleProcessor(chatModel, titleOptionsBuilder, advisor, conversationService, titleSanitizer);
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
                    org.assertj.core.api.Assertions.assertThat(sse.data().getType())
                            .isEqualTo(ChatEventType.TITLE);
                    org.assertj.core.api.Assertions.assertThat(sse.data().getData())
                            .isEqualTo("My Title");
                })
                .verifyComplete();

        verify(conversationService).persistGeneratedTitle("u-1", "My Title");
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

        org.assertj.core.api.Assertions.assertThat(output)
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
                .assertNext(sse -> org.assertj.core.api.Assertions.assertThat(sse.data().getData())
                        .isEqualTo("Quoted"))
                .verifyComplete();

        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(conversationService).persistGeneratedTitle(eq("u-2"), raw.capture());
        org.assertj.core.api.Assertions.assertThat(raw.getValue()).isEqualTo("Title: \"Quoted\"\nextra");
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

        StepVerifier.create(processor.process(ctx)).verifyComplete();

        verify(conversationService).persistGeneratedTitle("u-blank", "   ");
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
