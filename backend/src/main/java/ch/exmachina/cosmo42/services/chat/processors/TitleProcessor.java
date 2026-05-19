package ch.exmachina.cosmo42.services.chat.processors;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TitleProcessor implements ChatProcessor {

    private static final String TITLE_SYSTEM_INSTRUCTION = """
            Generate a concise conversation title (max 5 words) for the user message.
            Reply with ONLY the title, no extra text.
            Use the same language as the user message.
            """;

    ChatModel chatModel;
    OpenAiChatOptions.Builder titleModelOptionsBuilder;
    ChatConversationService chatConversationService;
    TitleSanitizer titleSanitizer;

    @Override
    public Flux<ServerSentEvent<ChatResponseDTO>> process(ChatContext context) {
        if (!context.isNewChat()) {
            return Flux.empty();
        }

        String uuid = context.getChatUuid();
        String message = context.getRequest().message();
        log.info("Title generation requested uuid={} messageLength={}", uuid, message.length());
        log.debug("Title generation context uuid={} hasEventSink={} messagePreview='{}'",
                uuid,
                context.getEventSink() != null,
                preview(message));

        emitStatus(context, "Generating a title...");

        ChatClient client = ChatClient.builder(chatModel)
                .defaultOptions(titleModelOptionsBuilder)
                .defaultSystem(TITLE_SYSTEM_INSTRUCTION)
                .build();

        return Mono.fromCallable(() -> client.prompt(new Prompt(message)).call().content())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(raw -> {
                    log.info("Title generation succeeded uuid={} titleLength={} titlePreview='{}'",
                            uuid,
                            raw.length(),
                            preview(raw));
                    chatConversationService.persistGeneratedTitle(uuid, raw);
                    return Mono.justOrEmpty(titleSanitizer.sanitize(raw));
                })
                .map(title -> ServerSentEvent.<ChatResponseDTO>builder()
                        .data(ChatResponseDTO.builder()
                                .type(ChatEventType.TITLE)
                                .data(title)
                                .build())
                        .build())
                .flux()
                .onErrorResume(err -> {
                    log.warn("Title generation failed for uuid={}", uuid, err);
                    return Flux.empty();
                });
    }

    private void emitStatus(ChatContext context, String text) {
        var sink = context.getEventSink();
        if (sink != null) {
            sink.tryEmitNext(ServerSentEvent.<ChatResponseDTO>builder()
                    .data(ChatResponseDTO.builder()
                            .type(ChatEventType.STATUS)
                            .data(text)
                            .build())
                    .build());
        }
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 77) + "...";
    }
}