package ch.exmachina.cosmo42.services.chat.tools;

import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.services.chat.ChatAttribute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

@Slf4j
public abstract class BaseTool {

    protected static String stripJsonFences(String raw) {
        if (raw == null) return null;
        String s = raw.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\n?", "").replaceFirst("```$", "").strip();
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    protected void emitStatus(ToolContext context, String message) {
        emitEvent(context, ChatEventType.STATUS, message);
    }

    @SuppressWarnings("unchecked")
    private void emitEvent(ToolContext context, ChatEventType type, String data) {
        try {
            Object sinkObj = context.getContext().get(ChatAttribute.SINK.name());
            if (sinkObj instanceof Sinks.Many) {
                Sinks.Many<ServerSentEvent<ChatResponseDTO>> sink =
                        (Sinks.Many<ServerSentEvent<ChatResponseDTO>>) sinkObj;

                sink.tryEmitNext(ServerSentEvent.<ChatResponseDTO>builder()
                        .data(ChatResponseDTO.builder()
                                .type(type)
                                .data(data)
                                .build())
                        .build());
            }
        } catch (Exception e) {
            log.warn("Could not emit {} event", type, e);
        }
    }

}
