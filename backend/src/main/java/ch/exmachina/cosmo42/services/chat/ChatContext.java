package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@Builder
public class ChatContext {

    Sinks.Many<ServerSentEvent<ChatResponseDTO>> eventSink;

    boolean newChat;
    String chatUuid;
    ChatRequestDTO request;

}
