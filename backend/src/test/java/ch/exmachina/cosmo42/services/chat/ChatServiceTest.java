package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.processors.ConversationProcessor;
import ch.exmachina.cosmo42.services.chat.processors.TitleProcessor;
import ch.exmachina.cosmo42.services.chat.processors.UuidProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

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
        when(conversationProcessor.process(any())).thenReturn(Flux.<ServerSentEvent<ChatResponseDTO>>empty());
        chatService = new ChatService(uuidProcessor, titleProcessor, conversationProcessor, conversationService);
    }

    @Test
    void newChatTriggersCreateIfAbsent() {
        chatService.processChat(new ChatRequestDTO(null, "hello"))
                .blockLast();

        ArgumentCaptor<String> uuidCap = ArgumentCaptor.forClass(String.class);
        verify(conversationService).createIfAbsent(uuidCap.capture());
        assertThat(uuidCap.getValue()).matches("^[0-9a-f-]{36}$");
    }

    @Test
    void existingChatDoesNotTriggerCreateIfAbsent() {
        chatService.processChat(new ChatRequestDTO("existing-uuid", "hello"))
                .blockLast();

        verify(conversationService, never()).createIfAbsent(any());
    }
}
