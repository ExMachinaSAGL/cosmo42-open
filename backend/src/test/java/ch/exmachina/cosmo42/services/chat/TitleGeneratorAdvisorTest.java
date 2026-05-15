package ch.exmachina.cosmo42.services.chat;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TitleGeneratorAdvisorTest {

    @Test
    void rewritesPromptWithTitleInstruction() {
        TitleGeneratorAdvisor advisor = new TitleGeneratorAdvisor();
        advisor.setPromptTemplate("Generate a title for: %s");

        Prompt original = new Prompt(new UserMessage("How do I deploy?"));
        ChatClientRequest req = ChatClientRequest.builder()
                .prompt(original)
                .context(new HashMap<>())
                .build();

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientResponse expected = mock(ChatClientResponse.class);
        when(chain.nextCall(any())).thenReturn(expected);

        ChatClientResponse result = advisor.adviseCall(req, chain);

        ArgumentCaptor<ChatClientRequest> captor = ArgumentCaptor.forClass(ChatClientRequest.class);
        org.mockito.Mockito.verify(chain).nextCall(captor.capture());
        String rewritten = captor.getValue().prompt().getUserMessage().getText();
        assertThat(rewritten).contains("Generate a title for: How do I deploy?");
        assertThat(result).isSameAs(expected);
    }

    @Test
    void getNameAndOrderAreStable() {
        TitleGeneratorAdvisor advisor = new TitleGeneratorAdvisor();
        assertThat(advisor.getName()).isEqualTo("TitleGeneratorAdvisor");
        assertThat(advisor.getOrder()).isEqualTo(100);
    }
}
