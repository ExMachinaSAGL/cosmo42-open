package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.entities.ChatConversation;
import ch.exmachina.cosmo42.exceptions.ChatConversationNotFoundException;
import ch.exmachina.cosmo42.repositories.ChatConversationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Slf4j
public class ChatConversationService {

    private final ChatConversationRepository repository;
    private final ChatMemory chatMemory;
    private final JdbcTemplate jdbcTemplate;
    private final TitleSanitizer titleSanitizer;
    private final TitleGeneratorAdvisor titleGeneratorAdvisor;
    private final ChatModel chatModel;
    private final OpenAiChatOptions.Builder titleModelOptionsBuilder;
    private final Clock clock;

    public ChatConversationService(
            ChatConversationRepository repository,
            ChatMemory chatMemory,
            JdbcTemplate jdbcTemplate,
            TitleSanitizer titleSanitizer,
            TitleGeneratorAdvisor titleGeneratorAdvisor,
            ChatModel chatModel,
            OpenAiChatOptions.Builder titleModelOptionsBuilder,
            Clock clock) {
        this.repository = repository;
        this.chatMemory = chatMemory;
        this.jdbcTemplate = jdbcTemplate;
        this.titleSanitizer = titleSanitizer;
        this.titleGeneratorAdvisor = titleGeneratorAdvisor;
        this.chatModel = chatModel;
        this.titleModelOptionsBuilder = titleModelOptionsBuilder;
        this.clock = clock;
    }

    @Transactional
    public ChatConversation createIfAbsent(String uuid) {
        return repository.findByUuid(uuid).orElseGet(() -> {
            ChatConversation c = new ChatConversation();
            c.setUuid(uuid);
            c.setTitle(null);
            LocalDateTime now = LocalDateTime.now(clock);
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            try {
                return repository.save(c);
            } catch (DataIntegrityViolationException race) {
                log.debug("Race-condition insert for uuid={}, resolving by re-fetch", uuid);
                return repository.findByUuid(uuid)
                        .orElseThrow(() -> race);
            }
        });
    }

    @Transactional
    public void persistGeneratedTitle(String uuid, String rawTitle) {
        log.debug("Persisting generated title uuid={} rawTitleLength={}",
                uuid,
                rawTitle == null ? 0 : rawTitle.length());
        var sanitized = titleSanitizer.sanitize(rawTitle);
        if (sanitized.isEmpty()) {
            log.debug("Skipping title persistence for uuid={} (blank after sanitization)", uuid);
            return;
        }
        int rows = repository.updateTitleByUuid(uuid, sanitized.get(), LocalDateTime.now(clock));
        if (rows == 0) {
            log.warn("Title write missed for uuid={} (row not found)", uuid);
            return;
        }
        log.info("Generated title persisted uuid={} title='{}'", uuid, sanitized.get());
    }

    @Transactional(readOnly = true)
    public Page<ChatConversation> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public ChatConversationWithMessages get(String uuid) {
        ChatConversation c = repository.findByUuid(uuid)
                .orElseThrow(() -> new ChatConversationNotFoundException(uuid));
        return new ChatConversationWithMessages(c, chatMemory.get(uuid));
    }

    @Transactional
    public ChatConversation rename(String uuid, String newTitle) {
        String sanitized = titleSanitizer.sanitize(newTitle)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Title is empty after sanitization"));
        int rows = repository.updateTitleByUuid(uuid, sanitized, LocalDateTime.now(clock));
        if (rows == 0) {
            throw new ChatConversationNotFoundException(uuid);
        }
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new ChatConversationNotFoundException(uuid));
    }

    @Transactional
    public ChatConversation regenerateTitle(String uuid) {
        log.info("Title regeneration requested uuid={}", uuid);
        repository.findByUuid(uuid)
                .orElseThrow(() -> new ChatConversationNotFoundException(uuid));

        String firstUserMessage = chatMemory.get(uuid).stream()
                .filter(m -> m.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No user message found for chat " + uuid));

        ChatClient client = ChatClient.builder(chatModel)
                .defaultOptions(titleModelOptionsBuilder)
                .defaultAdvisors(titleGeneratorAdvisor)
                .build();

        String raw = client.prompt(new Prompt(firstUserMessage))
                .call()
                .content();

        log.info("Title regeneration succeeded uuid={} titleLength={}",
                uuid,
                raw == null ? 0 : raw.length());

        persistGeneratedTitle(uuid, raw);

        return repository.findByUuid(uuid)
                .orElseThrow(() -> new ChatConversationNotFoundException(uuid));
    }

    @Transactional
    public void delete(String uuid) {
        jdbcTemplate.update(
                "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?", uuid);
        int rows = repository.deleteByUuid(uuid);
        if (rows == 0) {
            throw new ChatConversationNotFoundException(uuid);
        }
    }
}
