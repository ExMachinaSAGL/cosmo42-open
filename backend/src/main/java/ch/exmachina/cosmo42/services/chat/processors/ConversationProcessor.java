package ch.exmachina.cosmo42.services.chat.processors;

import ch.exmachina.cosmo42.config.ChatProperties;
import ch.exmachina.cosmo42.dto.ChatEventType;
import ch.exmachina.cosmo42.dto.ChatResponseDTO;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.chat.ChatAttribute;
import ch.exmachina.cosmo42.services.chat.ChatContext;
import ch.exmachina.cosmo42.services.chat.tools.KBDocumentSimilaritySearchTool;
import ch.exmachina.cosmo42.services.kb.MarkdownLinkProcessor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationProcessor implements ChatProcessor {

    ChatModel chatModel;
    OpenAiChatOptions.Builder chatModelOptionsBuilder;
    ChatMemory chatMemory;
    KBDocumentSimilaritySearchTool kbDocumentSimilaritySearchTool;
    KBDocumentRepository kbDocumentRepository;
    MarkdownLinkProcessor markdownLinkProcessor;
    ChatProperties chatProps;

    @Override
    public Flux<ServerSentEvent<ChatResponseDTO>> process(ChatContext context) {
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultOptions(chatModelOptionsBuilder)
                .defaultSystem(chatProps.getSystemInstruction())
                .defaultTools(kbDocumentSimilaritySearchTool)
                .build();

        // MessageChatMemoryAdvisor.adviseStream() persists the user message (in before)
        // and the aggregated assistant response (in after, via ChatClientMessageAggregator).
        // No explicit chatMemory.add() needed at the end of the stream.
        MessageChatMemoryAdvisor chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .order(20)
                .build();

        List<KBDocument> allKbDocuments = kbDocumentRepository.findAll();

        return chatClient.prompt()
                .user(context.getRequest().message())
                .advisors(chatMemoryAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, context.getChatUuid()))
                .toolContext(buildToolContext(context))
                .stream()
                .chatResponse()
                .bufferUntil(response -> response.getResult() != null &&
                        response.getResult().getOutput().getText() != null &&
                        response.getResult().getOutput().getText().endsWith("\n")) // buffer response tokens until newline character is encountered
                .map(responses -> {
                    String text = "";
                    for (ChatResponse response : responses) {
                        String raw = (response.getResult() != null
                                && response.getResult().getOutput().getText() != null)
                                ? response.getResult().getOutput().getText()
                                : "";
                        text += raw;
                    }

                    text = markdownLinkProcessor.replaceFileReferenceLinks(text, allKbDocuments);

                    return ServerSentEvent.<ChatResponseDTO>builder()
                            .data(ChatResponseDTO.builder()
                                    .type(ChatEventType.CHUNK)
                                    .data(text)
                                    .build())
                            .build();
                });
    }

    private Map<String, Object> buildToolContext(ChatContext context) {
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(ChatAttribute.SINK.name(), context.getEventSink());
        return toolContext;
    }
}
