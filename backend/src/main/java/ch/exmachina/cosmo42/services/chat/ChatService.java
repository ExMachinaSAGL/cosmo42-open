package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatMessageDTO;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.entities.ChatMessage;
import ch.exmachina.cosmo42.repositories.ChatHistoryRepository;
import ch.exmachina.cosmo42.services.chat.processors.ConversationProcessor;
import ch.exmachina.cosmo42.services.chat.processors.UuidProcessor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    UuidProcessor uuidProcessor;
    ConversationProcessor conversationProcessor;
    ChatHistoryRepository chatHistoryRepository;

    public Flux<ServerSentEvent<ChatResponseDTO>> processChat(ChatRequestDTO request) {
        String chatUuid = request.uuid() != null ? request.uuid() : UUID.randomUUID().toString();

        Sinks.Many<ServerSentEvent<ChatResponseDTO>> eventSink = Sinks.many().multicast().onBackpressureBuffer();

        ChatContext context = ChatContext.builder()
                .newChat(request.uuid() == null)
                .request(request)
                .chatUuid(chatUuid)
                .eventSink(eventSink)
                .build();

        eventSink.tryEmitNext(ServerSentEvent.<ChatResponseDTO>builder()
                .data(ChatResponseDTO.builder()
                        .type(ChatEventType.STATUS)
                        .data("Analyzing the request...")
                        .build())
                .build());

        List<Flux<ServerSentEvent<ChatResponseDTO>>> fluxes = new ArrayList<>();
        fluxes.add(uuidProcessor.process(context));
        fluxes.add(conversationProcessor.process(context));

        Flux<ServerSentEvent<ChatResponseDTO>> processes = Flux.merge(fluxes)
                .doOnComplete(() -> {
                    eventSink.tryEmitNext(ServerSentEvent.<ChatResponseDTO>builder()
                            .data(ChatResponseDTO.builder()
                                    .type(ChatEventType.COMPLETED)
                                    .build())
                            .build());
                    eventSink.tryEmitComplete();
                })
                .doOnError(e -> {
                    log.error("Chat processing failed", e);
                    eventSink.tryEmitNext(ServerSentEvent.<ChatResponseDTO>builder()
                            .data(ChatResponseDTO.builder()
                                    .type(ChatEventType.COMPLETED)
                                    .build())
                            .build());
                    eventSink.tryEmitComplete();
                })
                .onErrorComplete();

        return Flux.merge(eventSink.asFlux(), processes);
    }

    public List<ChatMessageDTO> getHistory(String conversationId) {
        return chatHistoryRepository.findByConversationId(conversationId).stream().map(
                chatMessage -> ChatMessageDTO.builder()
                        .content(chatMessage.getContent())
                        .timestamp(chatMessage.getTimestamp())
                        .type(chatMessage.getType())
                        .build()
        ).toList();
    }

    @Transactional
    public void deleteHistory(String conversationId) {
        List<ChatMessage> history = chatHistoryRepository.findByConversationId(conversationId);
        if(history.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        chatHistoryRepository.deleteByConversationId(conversationId);
    }
}
