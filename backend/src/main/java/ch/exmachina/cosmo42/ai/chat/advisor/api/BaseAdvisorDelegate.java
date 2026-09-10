package ch.exmachina.cosmo42.ai.chat.advisor.api;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

@AllArgsConstructor
public abstract class BaseAdvisorDelegate implements BaseChatMemoryAdvisor {
	
	private final BaseAdvisor advisor;

	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		return advisor.adviseCall(chatClientRequest, callAdvisorChain);
	}
	
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
	        StreamAdvisorChain streamAdvisorChain) {
	    return advisor.adviseStream(chatClientRequest, streamAdvisorChain);
	}	

	public int getOrder() {
		return advisor.getOrder();
	}

	public String getName() {
		return advisor.getName();
	}

	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		return advisor.before(chatClientRequest, advisorChain);
	}

	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		return advisor.after(chatClientResponse, advisorChain);
	}

	public Scheduler getScheduler() {
		return advisor.getScheduler();
	}
}
