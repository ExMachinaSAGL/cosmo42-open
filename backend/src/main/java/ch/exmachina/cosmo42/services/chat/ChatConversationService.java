package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.entities.ChatConversation;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.exceptions.ChatConversationNotFoundException;
import ch.exmachina.cosmo42.exceptions.InvalidChatTitleException;
import ch.exmachina.cosmo42.repositories.ChatConversationRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.kb.MarkdownLinkProcessor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class ChatConversationService {

    private final ChatConversationRepository repository;
    private final ChatMemory chatMemory;
    private final TitleSanitizer titleSanitizer;
    private final Clock clock;
    private final KBDocumentRepository kbDocumentRepository;

    public ChatConversationService(
            ChatConversationRepository repository,
            ChatMemory chatMemory,
            TitleSanitizer titleSanitizer,
            Clock clock,
            KBDocumentRepository kbDocumentRepository) {
        this.repository = repository;
        this.chatMemory = chatMemory;
        this.titleSanitizer = titleSanitizer;
        this.clock = clock;
        this.kbDocumentRepository = kbDocumentRepository;
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
    public Optional<String> persistGeneratedTitle(String uuid, String rawTitle) {
        log.debug("Persisting generated title uuid={} rawTitleLength={}",
                uuid,
                rawTitle == null ? 0 : rawTitle.length());
        var sanitized = titleSanitizer.sanitize(rawTitle);
        if (sanitized.isEmpty()) {
            log.debug("Skipping title persistence for uuid={} (blank after sanitization)", uuid);
            return Optional.empty();
        }
        int rows = repository.updateTitleByUuid(uuid, sanitized.get(), LocalDateTime.now(clock));
        if (rows == 0) {
            log.warn("Title write missed for uuid={} (row not found)", uuid);
            return Optional.empty();
        }
        log.info("Generated title persisted uuid={} title='{}'", uuid, sanitized.get());
        return sanitized;
    }

    @Transactional(readOnly = true)
    public Page<ChatConversation> list(Pageable pageable) {
        return repository.findAllByOrderByUpdatedAtDesc(pageable);
    }

    @Transactional
    public void markActive(String uuid) {
        int rows = repository.updateActivityByUuid(uuid, LocalDateTime.now(clock));
        if (rows == 0) {
            throw new ChatConversationNotFoundException(uuid);
        }
    }

    @Transactional(readOnly = true)
    public ChatConversationWithMessages get(String uuid) {
        ChatConversation c = repository.findByUuid(uuid)
                .orElseThrow(() -> new ChatConversationNotFoundException(uuid));
        List<Message> messages = chatMemory.get(uuid);

        MarkdownLinkProcessor markdownLinkProcessor = new MarkdownLinkProcessor();
        List<KBDocument> allKbDocuments = kbDocumentRepository.findAll();

        messages = messages.stream().map(msg -> {
            if(msg instanceof AssistantMessage) {
                String newContent = markdownLinkProcessor.replaceFileReferenceLinks(msg.getText(), allKbDocuments);
                return new AssistantMessage(newContent);
            } else {
                return msg;
            }
        }).toList();
        return new ChatConversationWithMessages(c, messages);
    }

    @Transactional
    public ChatConversation rename(String uuid, String newTitle) {
        String sanitized = titleSanitizer.sanitize(newTitle)
                .orElseThrow(() -> new InvalidChatTitleException(
                        "Title is empty after sanitization"));
        int rows = repository.updateTitleByUuid(uuid, sanitized, LocalDateTime.now(clock));
        if (rows == 0) {
            throw new ChatConversationNotFoundException(uuid);
        }
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new ChatConversationNotFoundException(uuid));
    }

    @Transactional
    public void delete(String uuid) {
        int rows = repository.deleteByUuid(uuid);
        if (rows == 0) {
            throw new ChatConversationNotFoundException(uuid);
        }
        chatMemory.clear(uuid);
    }
}
