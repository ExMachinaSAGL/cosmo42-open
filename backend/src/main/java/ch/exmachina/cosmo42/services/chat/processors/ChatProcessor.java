package ch.exmachina.cosmo42.services.chat.processors;

import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.ChatContext;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface ChatProcessor {

    Flux<ServerSentEvent<ChatResponseDTO>> process(ChatContext context);

}
