package ch.exmachina.cosmo42.services.chat.processors;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.ChatContext;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class UuidProcessor implements ChatProcessor {

    @Override
    public Flux<ServerSentEvent<ChatResponseDTO>> process(ChatContext context) {
        if (context.getRequest().uuid() != null) {
            return Flux.empty();
        }

        return Flux.just(
                ServerSentEvent.<ChatResponseDTO>builder()
                        .data(ChatResponseDTO.builder()
                                .type(ChatEventType.UUID)
                                .data(context.getChatUuid())
                                .build())
                        .build());
    }

}
