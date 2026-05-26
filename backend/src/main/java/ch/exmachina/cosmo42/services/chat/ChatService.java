package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatRequestDTO;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.processors.ConversationProcessor;
import ch.exmachina.cosmo42.services.chat.processors.TitleProcessor;
import ch.exmachina.cosmo42.services.chat.processors.UuidProcessor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    UuidProcessor uuidProcessor;
    TitleProcessor titleProcessor;
    ConversationProcessor conversationProcessor;
    ChatConversationService chatConversationService;
    Supplier<String> uuidSupplier;

    public Flux<ServerSentEvent<ChatResponseDTO>> processChat(ChatRequestDTO request) {
        boolean isNewChat = request.uuid() == null;
        String chatUuid = isNewChat ? uuidSupplier.get() : request.uuid();

        chatConversationService.createIfAbsent(chatUuid);
        chatConversationService.markActive(chatUuid);

        Sinks.Many<ServerSentEvent<ChatResponseDTO>> eventSink = Sinks.many().multicast().onBackpressureBuffer();

        ChatContext context = ChatContext.builder()
                .newChat(isNewChat)
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
        fluxes.add(titleProcessor.process(context));
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
                                    .type(ChatEventType.ERROR)
                                    .data(e.getMessage())
                                    .build())
                            .build());
                    eventSink.tryEmitComplete();
                })
                .onErrorComplete();

        return Flux.merge(eventSink.asFlux(), processes);
    }

}
