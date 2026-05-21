package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.processors.ConversationProcessor;
import ch.exmachina.cosmo42.services.chat.processors.TitleProcessor;
import ch.exmachina.cosmo42.services.chat.processors.UuidProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    UuidProcessor uuidProcessor;
    TitleProcessor titleProcessor;
    ConversationProcessor conversationProcessor;
    ChatConversationService conversationService;
    ChatService chatService;

    @BeforeEach
    void setUp() {
        uuidProcessor = mock(UuidProcessor.class);
        titleProcessor = mock(TitleProcessor.class);
        conversationProcessor = mock(ConversationProcessor.class);
        conversationService = mock(ChatConversationService.class);
        when(uuidProcessor.process(any())).thenReturn(Flux.<ServerSentEvent<ChatResponseDTO>>empty());
        when(titleProcessor.process(any())).thenReturn(Flux.<ServerSentEvent<ChatResponseDTO>>empty());
        chatService = new ChatService(uuidProcessor, titleProcessor, conversationProcessor,
                conversationService, () -> "fixed-test-uuid");
    }

    @Nested
    class Orchestration {

        @BeforeEach
        void setUp() {
            when(conversationProcessor.process(any())).thenReturn(Flux.<ServerSentEvent<ChatResponseDTO>>empty());
        }

        @Test
        void newChatTriggersCreateIfAbsent() {
            chatService.processChat(new ChatRequestDTO(null, "hello"))
                    .blockLast();

            ArgumentCaptor<String> uuidCap = ArgumentCaptor.forClass(String.class);
            verify(conversationService).createIfAbsent(uuidCap.capture());
            assertThat(uuidCap.getValue()).isEqualTo("fixed-test-uuid");
        }

        @Test
        void existingChatEnsuresConversationRowExists() {
            chatService.processChat(new ChatRequestDTO("existing-uuid", "hello"))
                    .blockLast();

            verify(conversationService).createIfAbsent("existing-uuid");
        }

        @Test
        void marksConversationAsActiveOnEachRequest() {
            chatService.processChat(new ChatRequestDTO("u-active", "hi"))
                    .blockLast();

            verify(conversationService).markActive("u-active");
        }

        @Test
        void emitsStatusEventBeforeProcessorEvents() {
            List<ChatResponseDTO> events = chatService.processChat(new ChatRequestDTO(null, "hi"))
                    .map(ServerSentEvent::data)
                    .take(1)
                    .collectList()
                    .block();

            assertThat(events).hasSize(1);
            assertThat(events.get(0).getType()).isEqualTo(ChatEventType.STATUS);
            assertThat(events.get(0).getData()).isEqualTo("Analyzing the request...");
        }

        @Test
        void invokesAllThreeProcessors() {
            chatService.processChat(new ChatRequestDTO(null, "hi"))
                    .blockLast();

            verify(uuidProcessor).process(any());
            verify(titleProcessor).process(any());
            verify(conversationProcessor).process(any());
        }

        @Test
        void createIfAbsentCalledBeforeMarkActive() {
            chatService.processChat(new ChatRequestDTO(null, "hi"))
                    .blockLast();

            var inOrder = inOrder(conversationService);
            inOrder.verify(conversationService).createIfAbsent(any());
            inOrder.verify(conversationService).markActive(any());
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void downstreamFailureEmitsErrorEventNotCompleted() {
            when(conversationProcessor.process(any())).thenReturn(
                    Flux.error(new RuntimeException("LLM upstream down")));

            List<ChatResponseDTO> events = chatService.processChat(new ChatRequestDTO(null, "hi"))
                    .map(ServerSentEvent::data)
                    .collectList()
                    .block();

            assertThat(events).isNotNull();
            assertThat(events)
                    .extracting(ChatResponseDTO::getType)
                    .doesNotContain(ChatEventType.COMPLETED)
                    .contains(ChatEventType.ERROR);
        }

        @Test
        void errorEventCarriesUpstreamMessageInData() {
            when(conversationProcessor.process(any())).thenReturn(
                    Flux.error(new RuntimeException("LLM upstream down")));

            ChatResponseDTO errorEvent = chatService.processChat(new ChatRequestDTO(null, "hi"))
                    .map(ServerSentEvent::data)
                    .filter(d -> d.getType() == ChatEventType.ERROR)
                    .blockFirst();

            assertThat(errorEvent).isNotNull();
            assertThat(errorEvent.getData()).isEqualTo("LLM upstream down");
        }

        @Test
        void successPathStillEmitsCompletedWithoutError() {
            when(conversationProcessor.process(any())).thenReturn(Flux.empty());

            List<ChatResponseDTO> events = chatService.processChat(new ChatRequestDTO(null, "hi"))
                    .map(ServerSentEvent::data)
                    .collectList()
                    .block();

            assertThat(events).isNotNull();
            assertThat(events).extracting(ChatResponseDTO::getType)
                    .contains(ChatEventType.COMPLETED)
                    .doesNotContain(ChatEventType.ERROR);
        }

        @Test
        void streamCompletesCleanlyAfterError() {
            when(conversationProcessor.process(any())).thenReturn(
                    Flux.error(new RuntimeException("boom")));

            var events = chatService.processChat(new ChatRequestDTO(null, "hi"))
                    .collectList()
                    .block(Duration.ofSeconds(3));

            assertThat(events).isNotNull();
        }
    }
}
