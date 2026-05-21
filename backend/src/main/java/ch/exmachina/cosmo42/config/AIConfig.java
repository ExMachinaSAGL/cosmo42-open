package ch.exmachina.cosmo42.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    @Value("${cosmo42.model.llm.name}")
    private String llmModelName;

    @Value("${cosmo42.model.embedding.name}")
    private String embeddingModelName;

    @Value("${cosmo42.chunking.max-tokens}")
    private Integer chunkingMaxTokens;

    @Bean
    public OpenAiChatOptions.Builder chatModelOptionsBuilder() {
        return OpenAiChatOptions.builder()
                .temperature(0.2)
                .topP(0.9)
                .maxTokens(1024)
                .model(llmModelName);
    }

    @Bean
    public OpenAiChatOptions.Builder chunkerModelOptionsBuilder() {
        return OpenAiChatOptions.builder()
                .temperature(0.1)
                .topP((double) 1.0F)
                .maxTokens(chunkingMaxTokens)
                .model(llmModelName);
    }

    @Bean
    public OpenAiChatOptions.Builder titleModelOptionsBuilder() {
        return OpenAiChatOptions.builder()
                .temperature(0.3)
                .topP(0.9)
                .maxTokens(32)
                .model(llmModelName);
    }

    @Bean
    public OpenAiEmbeddingOptions embeddingModelOptions() {
        return OpenAiEmbeddingOptions.builder()
                .model(embeddingModelName)
                .build();
    }

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository jdbcRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcRepository)
                .maxMessages(25)
                .build();
    }

}
