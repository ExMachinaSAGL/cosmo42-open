package ch.exmachina.cosmo42.services.chat.processors;

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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
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

    private static final String system_instruction = """
            You are cosmo42, an expert in retrieving information from a private knowledge base.
            You are integrated into a system where users upload a variety of files; you have access to a visual LLM and a semantic search engine that allow you to find the requested information and indicate the files from which it originates.

            SEARCH FLOW (RAG) - MANDATORY EXECUTION:
            If the question falls within the ALLOWED DOMAIN (even in the case of historical events or general questions about the territory), you must NEVER answer from memory, but you MUST ALWAYS:
            1. Extract the key concepts from the user's request (e.g., dates, places, events).
            2. IMMEDIATELY call the tool at your disposal (e.g., `search_knowledge_base`) to search the knowledge base.
            3. Analyze the context "chunks" returned by the tool.

            POST-SEARCH RESPONSE RULES:
            - FACT-BASED ONLY: Build your response EXCLUSIVELY on the documents retrieved by the tool.\s
            - NO INFORMATION FOUND: If, and ONLY IF, you have called the tool and it has returned nothing useful to answer, state: "I have not found specific information in the documents at my disposal regarding this request." At this point, you may draw upon your prior knowledge to answer the user.
            - TRANSPARENCY: Always specify which files your information comes from using the REF_FILE_{UUID} convention (for example, REF_FILE_e58ed763-928c-4155-bee9-fdbaaadc15f3). CRUCIAL: NEVER INVENT A UUID; solely and exclusively use the file UUIDs indicated in the context provided to you.
            - TONE AND STYLE: Maintain a professional, reassuring, and clear tone. Use bullet points to describe complex procedures or data.
        """;

    @Override
    public Flux<ServerSentEvent<ChatResponseDTO>> process(ChatContext context) {
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultOptions(chatModelOptionsBuilder)
                .defaultSystem(system_instruction)
                .defaultTools(kbDocumentSimilaritySearchTool)
                .build();

        MessageChatMemoryAdvisor chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .order(20)
                .build();

        var promptRequest = chatClient.prompt()
                .user(context.getRequest().message())
                .advisors(chatMemoryAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, context.getChatUuid()));

        StringBuilder fullResponse = new StringBuilder();
        MarkdownLinkProcessor markdownLinkProcessor = new MarkdownLinkProcessor();
        List<KBDocument> allKbDocuments = kbDocumentRepository.findAll();

        return promptRequest
                .toolContext(buildToolContext(context))
                .stream()
                .chatResponse()
                .bufferUntil(response -> response.getResult() != null &&
                        response.getResult().getOutput().getText() != null &&
                        response.getResult().getOutput().getText().endsWith("\n"))
                .map(responses -> {
                    System.out.println("start processing buffer of: "+responses.size());

                            String text = "";
                            for(ChatResponse response : responses) {
                                if (response.getResult() != null) {
                                    String raw = response.getResult().getOutput().getText();
                                    if (raw != null) {
                                        text += raw;
                                    }
                                }
                            }

                    System.out.println("buffer item: "+text);


                    text = markdownLinkProcessor.replaceFileReferenceLinks(text, allKbDocuments);

                            fullResponse.append(text);
                            return ServerSentEvent.<ChatResponseDTO>builder()
                                    .data(
                                            ChatResponseDTO.builder()
                                                    .type(ChatEventType.CHUNK)
                                                    .data(text)
                                                    .build())
                                    .build();
                        })
                .doOnComplete(
                        () -> chatMemory.add(
                                    context.getChatUuid(),
                                    List.of(
                                            new UserMessage(context.getRequest().message()),
                                            new AssistantMessage(fullResponse.toString())))
                        );
    }

    private Map<String, Object> buildToolContext(ChatContext context) {
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(ChatAttribute.SINK.name(), context.getEventSink());
        return toolContext;
    }
}
