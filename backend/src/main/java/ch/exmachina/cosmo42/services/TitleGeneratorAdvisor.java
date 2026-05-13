package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.ChatAttribute;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class TitleGeneratorAdvisor implements CallAdvisor {

    @Override
    @SuppressWarnings("unchecked")
    public @NonNull ChatClientResponse adviseCall(
            @NonNull ChatClientRequest chatClientRequest,
            @NonNull CallAdvisorChain callAdvisorChain
    ) {
        Sinks.Many<ServerSentEvent<ChatResponseDTO>> eventSink =
                (Sinks.Many<ServerSentEvent<ChatResponseDTO>>) chatClientRequest.context().get(ChatAttribute.SINK.name());

        if (eventSink != null) {
            eventSink.tryEmitNext(ServerSentEvent.<ChatResponseDTO>builder()
                    .data(ChatResponseDTO.builder()
                            .type(ChatEventType.STATUS)
                            .data("Generating a title...")
                            .build())
                    .build());
        }

        String titlePrompt =
                """
                        Based on this user message, generate a concise conversation title (max 5 words).
                        Reply with ONLY the title, no extra text.
                        Use the same language as the message when generating the title.
                        User message:
                        
                        %s
                        """.formatted(chatClientRequest.prompt().getUserMessage().getText());

        ChatClientRequest modifiedRequest = chatClientRequest.mutate()
                .prompt(new Prompt(titlePrompt, chatClientRequest.prompt().getOptions()))
                .build();

        return callAdvisorChain.nextCall(modifiedRequest);
    }

    @Override
    public @NonNull String getName() {
        return "TitleGeneratorAdvisor";
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
