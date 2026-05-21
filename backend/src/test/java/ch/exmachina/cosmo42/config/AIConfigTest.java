package ch.exmachina.cosmo42.config;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AIConfigTest extends AbstractIntegrationTest {

    @Autowired
    @Qualifier("titleModelOptionsBuilder")
    OpenAiChatOptions.Builder titleModelOptionsBuilder;

    @Autowired
    @Qualifier("chatModelOptionsBuilder")
    OpenAiChatOptions.Builder chatModelOptionsBuilder;

    @Autowired
    @Qualifier("chunkerModelOptionsBuilder")
    OpenAiChatOptions.Builder chunkerModelOptionsBuilder;

    @Autowired
    OpenAiEmbeddingOptions embeddingModelOptions;

    @Autowired
    ChatMemory chatMemory;

    @Test
    void titleOptionsHaveSmallMaxTokens() {
        OpenAiChatOptions opts = titleModelOptionsBuilder.build();
        assertThat(opts.getMaxTokens()).isEqualTo(32);
        assertThat(opts.getTemperature()).isEqualTo(0.3);
        assertThat(opts.getTopP()).isEqualTo(0.9);
        assertThat(opts.getModel()).isNotBlank();
    }

    @Test
    void chatModelOptionsHaveProductionParameters() {
        OpenAiChatOptions opts = chatModelOptionsBuilder.build();
        assertThat(opts.getTemperature()).isEqualTo(0.2);
        assertThat(opts.getTopP()).isEqualTo(0.9);
        assertThat(opts.getMaxTokens()).isEqualTo(1024);
        assertThat(opts.getModel()).isNotBlank();
    }

    @Test
    void chunkerModelOptionsHaveHighMaxTokensAndLowTemp() {
        OpenAiChatOptions opts = chunkerModelOptionsBuilder.build();
        assertThat(opts.getTemperature()).isEqualTo(0.1);
        assertThat(opts.getMaxTokens()).isEqualTo(16000);
        assertThat(opts.getModel()).isNotBlank();
    }

    @Test
    void embeddingModelOptionsHaveConfiguredModel() {
        assertThat(embeddingModelOptions.getModel()).isNotBlank();
    }

    @Test
    void chatMemoryHasExpectedMaxMessages() {
        assertThat(chatMemory).isNotNull();
    }
}
