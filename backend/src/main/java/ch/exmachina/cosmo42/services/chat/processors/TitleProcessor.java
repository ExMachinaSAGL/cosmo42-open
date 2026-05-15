package ch.exmachina.cosmo42.services.chat.processors;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.ChatAttribute;
import ch.exmachina.cosmo42.services.chat.ChatContext;
import ch.exmachina.cosmo42.services.chat.TitleGeneratorAdvisor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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
public class TitleProcessor implements ChatProcessor {

    ChatModel chatModel;
    OpenAiChatOptions.Builder chatModelOptionsBuilder;
    TitleGeneratorAdvisor titleGeneratorAdvisor;

    @Override
    public Flux<ServerSentEvent<ChatResponseDTO>> process(ChatContext context) {
        ChatClient titleGenerationClient = ChatClient.builder(chatModel)
                .defaultOptions(chatModelOptionsBuilder)
                .defaultAdvisors(titleGeneratorAdvisor)
                .build();

        if (!context.isNewChat()) {
            return Flux.empty();
        }

        return Mono.fromCallable(() -> titleGenerationClient
                        .prompt(new Prompt(context.getRequest().message()))
                        .advisors(spec -> spec.param(ChatAttribute.SINK.name(), context.getEventSink())
                        )
                        .call()
                        .chatResponse()
                ).subscribeOn(Schedulers.boundedElastic())
                .map(chatResponse -> {
                    String title = "Senza titolo";
                    if (chatResponse.getResult() != null) {
                        title = chatResponse.getResult().getOutput().getText();
                    }
                    title = title != null ? title : "Senza titolo";

                    return ServerSentEvent.<ChatResponseDTO>builder()
                            .data(ChatResponseDTO.builder()
                                    .type(ChatEventType.TITLE)
                                    .data(title)
                                    .build())
                            .build();
                })
                .flux();
    }
}
