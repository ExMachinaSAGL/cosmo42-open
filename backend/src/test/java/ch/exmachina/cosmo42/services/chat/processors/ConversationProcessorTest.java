package ch.exmachina.cosmo42.services.chat.processors;

import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.chat.ChatContext;
import ch.exmachina.cosmo42.services.chat.tools.KBDocumentSimilaritySearchTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationProcessorTest {

    ChatModel chatModel;
    ChatMemory chatMemory;
    KBDocumentSimilaritySearchTool tool;
    ConversationProcessor processor;
    KBDocumentRepository  kbDocumentRepository;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        chatMemory = mock(ChatMemory.class);
        tool = mock(KBDocumentSimilaritySearchTool.class);
        kbDocumentRepository = mock(KBDocumentRepository.class);
        processor = new ConversationProcessor(
                chatModel,
                OpenAiChatOptions.builder().model("test-model"),
                chatMemory,
                tool,
                kbDocumentRepository);

        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(chatMemory.get("u-1")).thenReturn(List.of());
    }

    @Test
    void doesNotManuallyPersistMessagesBecauseMemoryAdvisorOwnsPersistence() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(
                List.of(new Generation(new AssistantMessage("answer"))))));

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
