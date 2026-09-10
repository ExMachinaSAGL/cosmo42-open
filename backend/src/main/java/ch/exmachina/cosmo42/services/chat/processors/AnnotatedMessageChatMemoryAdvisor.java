package ch.exmachina.cosmo42.services.chat.processors;

import static ch.exmachina.cosmo42.services.chat.ChatAttribute.CITATIONS;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.Generation;

import ch.exmachina.cosmo42.ai.chat.advisor.api.BaseAdvisorDelegate;
import ch.exmachina.cosmo42.services.kb.CitationCollector;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AnnotatedMessageChatMemoryAdvisor extends BaseAdvisorDelegate {

    private final CitationCollector citationCollector;

    public AnnotatedMessageChatMemoryAdvisor(ChatMemory chatMemory, CitationCollector citationCollctor) {
        super(MessageChatMemoryAdvisor.builder(chatMemory)
                .order(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER) // so the memory advisor wraps the tool-call loop,
                                                                     // and the {@code ToolCallingAdvisor} manages its
                                                                     // own intermediate conversation history
                .build());
        this.citationCollector = citationCollctor;
    }
    
    // NOTE: this method is copied from MessageChatMemoryAdvisor to allow before and after to target "this" delegate.
    // Unfortunately MessageChatMemoryAdvisor is final and delegation has its limits
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {
        // Process the request with the before method
        return Mono.just(chatClientRequest)
            .publishOn(this.getScheduler())
            .map(request -> this.before(request, streamAdvisorChain))
            .flatMapMany(streamAdvisorChain::nextStream)
            .transform(flux -> new ChatClientMessageAggregator().aggregateChatClientResponse(flux,
                    response -> this.after(response, streamAdvisorChain)));
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        if (chatClientResponse.chatResponse() != null) {
            chatClientResponse.chatResponse()
                    .getResults().stream()
                    .map(Generation::getOutput)
                    .filter(message -> !message.getMetadata().containsKey(CITATIONS.name()))
                    .forEach(message -> {
                        var citations = citationCollector.assemble(message);
                        if (!citations.isEmpty()) {
                            message.getMetadata().put(CITATIONS.name(), citations);
                        }
                    });
        }
        return super.after(chatClientResponse, advisorChain);
    }
}
