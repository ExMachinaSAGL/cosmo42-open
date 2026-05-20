package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.processors.ConversationProcessor;
import ch.exmachina.cosmo42.services.chat.processors.TitleProcessor;
import ch.exmachina.cosmo42.services.chat.processors.UuidProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceErrorPathTest {

    UuidProcessor uuidProcessor;
    TitleProcessor titleProcessor;
    ConversationProcessor conversationProcessor;
    ChatConversationService chatConversationService;
    ChatService chatService;

    @BeforeEach
    void setUp() {
        uuidProcessor = mock(UuidProcessor.class);
        titleProcessor = mock(TitleProcessor.class);
        conversationProcessor = mock(ConversationProcessor.class);
        chatConversationService = mock(ChatConversationService.class);
        when(uuidProcessor.process(any())).thenReturn(Flux.<ServerSentEvent<ChatResponseDTO>>empty());
        when(titleProcessor.process(any())).thenReturn(Flux.<ServerSentEvent<ChatResponseDTO>>empty());
        chatService = new ChatService(uuidProcessor, titleProcessor, conversationProcessor, chatConversationService);
    }

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

        // collectList completes only if the upstream Flux signals onComplete; a leaked sink
        // (no tryEmitComplete on error) would hang forever. The default WebTestClient timeout
        // would catch it, but here we assert the Flux terminates within a few seconds.
        var events = chatService.processChat(new ChatRequestDTO(null, "hi"))
                .collectList()
                .block(java.time.Duration.ofSeconds(3));

        assertThat(events).isNotNull();
    }
}
