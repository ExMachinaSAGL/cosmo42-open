package ch.exmachina.cosmo42.ai.chat.memory;

import static ch.exmachina.cosmo42.services.chat.ChatAttribute.CITATIONS;
import static ch.exmachina.cosmo42.utils.Iterables.asRandomAccess;
import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.dao.DataAccessException;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ch.exmachina.cosmo42.dto.CitationEntryDTO;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public class JdbcChatMemoryWithMetadataRepository implements ChatMemoryRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcChatMemoryRepository.class);

    private static final TypeReference<List<CitationEntryDTO>> CITATIONS_TYPE = new TypeReference<List<CitationEntryDTO>>() {
    };

    private final JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    private final JdbcChatMemoryRepository baseRepository;

    private final ObjectMapper json;

    public JdbcChatMemoryWithMetadataRepository(
            JdbcTemplate jdbcTemplate,
            @Nullable PlatformTransactionManager txManager,
            JdbcChatMemoryRepository baseRepository,
            ObjectMapper json) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(txManager);
        this.baseRepository = baseRepository;
        this.json = json;
    }

    @Override
    public List<String> findConversationIds() {
        return transactionTemplate.execute(_ -> baseRepository.findConversationIds());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return transactionTemplate.execute(_ -> {
            var messages = asRandomAccess(baseRepository.findByConversationId(conversationId));
            jdbcTemplate.execute(
                    "SELECT sequence_id, `key`, value FROM CHAT_MEMORY_METADATA WHERE conversation_id=?",
                    new MetadataBatchSelectCallback(conversationId, messages));
            return messages;
        });
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        var indexedMessages = asRandomAccess(messages);
        transactionTemplate.executeWithoutResult(_ -> {
            baseRepository.saveAll(conversationId, indexedMessages);
            jdbcTemplate.execute(
                    "INSERT INTO CHAT_MEMORY_METADATA (conversation_id, sequence_id, `key`, value) VALUES (?,?,?,?)",
                    new MetadataBatchUpdateCallback(conversationId, indexedMessages));
        });
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        transactionTemplate.executeWithoutResult(_ -> baseRepository.deleteByConversationId(conversationId));
    }

    private <T> String serialize(T metadata, TypeReference<T> type) {
        try {
            return metadata == null ? null : json.writerFor(type).writeValueAsString(metadata);
        } catch (JacksonException e) {
            logger.error("Unable to serialize metadata: {}", metadata, e);
            return null;
        }
    }

    private <T> T deserialize(String metadata, TypeReference<T> type) {
        try {
            return metadata == null ? null : json.readerFor(type).readValue(metadata);
        } catch (JacksonException e) {
            logger.error("Unable to serialize metadata: {}", metadata, e);
            return null;
        }
    }

    private class MetadataBatchUpdateCallback implements PreparedStatementCallback<Void> {
        private final String conversationId;
        private final List<Pair<Integer, Map<String, Object>>> metadata;

        private <M extends List<Message> & RandomAccess> MetadataBatchUpdateCallback(String conversationId,
                M messages) {
            this.conversationId = conversationId;
            metadata = IntStream
                    .range(0, messages.size())
                    .mapToObj(i -> Pair.of(i, messages.get(i).getMetadata()))
                    .filter(metadata -> metadata.getSecond() != null)
                    .collect(toCollection(ArrayList::new));
        }

        @Override
        public Void doInPreparedStatement(PreparedStatement ps)
                throws SQLException, DataAccessException {
            for (var metadata : metadata) {
                var citations = serialize(citations(metadata.getSecond()), CITATIONS_TYPE);
                if (isBlank(citations)) {
                    continue;
                }
                ps.setString(1, conversationId);
                ps.setLong(2, metadata.getFirst());
                ps.setString(3, CITATIONS.name());
                ps.setString(4, citations);
                ps.addBatch();
            }
            ps.executeBatch();
            return null;
        }

        @SuppressWarnings("unchecked")
        private List<CitationEntryDTO> citations(Map<String, Object> metadata) {
            if (metadata.get(CITATIONS.name()) instanceof List<?> citations) {
                return (List<CitationEntryDTO>) citations;
            }
            return null;
        }
    }

    private class MetadataBatchSelectCallback implements PreparedStatementCallback<Void> {

        private final String conversationId;

        private final List<Message> messages;

        private <M extends List<Message> & RandomAccess> MetadataBatchSelectCallback(String conversationId,
                M messages) {
            this.conversationId = conversationId;
            this.messages = messages;
        }

        @Override
        public Void doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
            ps.setString(1, conversationId);
            var rs = ps.executeQuery();
            while (rs.next()) {
                var index = rs.getInt(1);
                var key = rs.getString(2);
                var value = deserialize(rs.getString(3), CITATIONS_TYPE);
                if (value != null) {
                    var message = messages.get(index);
                    message.getMetadata().put(key, value);
                }
            }
            return null;
        }

    }
}
