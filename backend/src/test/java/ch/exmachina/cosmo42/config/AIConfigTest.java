package ch.exmachina.cosmo42.config;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AIConfigTest extends AbstractIntegrationTest {

    @Autowired
    @Qualifier("titleModelOptionsBuilder")
    OpenAiChatOptions.Builder titleModelOptionsBuilder;

    @Test
    void titleOptionsHaveSmallMaxTokens() {
        OpenAiChatOptions opts = titleModelOptionsBuilder.build();
        assertThat(opts.getMaxTokens()).isEqualTo(32);
        assertThat(opts.getTemperature()).isEqualTo(0.3);
        assertThat(opts.getTopP()).isEqualTo(0.9);
        assertThat(opts.getModel()).isNotBlank();
    }
}
