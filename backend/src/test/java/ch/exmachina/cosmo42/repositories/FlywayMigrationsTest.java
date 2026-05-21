package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FlywayMigrationsTest extends AbstractIntegrationTest {

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void allDeclaredMigrationsApplyOnFreshContainer() {
        List<MigrationInfo> applied = Arrays.stream(flyway.info().applied()).toList();

        assertThat(applied)
                .extracting(m -> m.getVersion().getVersion())
                .contains("0", "1", "2", "3", "4");
    }

    @Test
    void noPendingMigrationsAfterStartup() {
        assertThat(flyway.info().pending())
                .as("Flyway should not report any pending migrations after Spring Boot startup")
                .isEmpty();
    }

    @Test
    void reRunningMigrateIsIdempotent() {
        int countBefore = countAppliedMigrations();

        flyway.migrate();
        flyway.migrate();
        flyway.migrate();

        assertThat(countAppliedMigrations())
                .as("repeated migrate() calls must not duplicate history entries")
                .isEqualTo(countBefore);
    }

    @Test
    void expectedTablesExistAfterMigration() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tables)
                .extracting(String::toLowerCase)
                .contains("kb_document", "kb_document_chunk", "spring_ai_chat_memory",
                        "chat_conversation", "ingestion_job", "ingestion_job_page");
    }

    @Test
    void kbDocumentChunkHasVector1024EmbeddingColumn() {
        String columnType = jdbcTemplate.queryForObject(
                "SELECT COLUMN_TYPE FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'kb_document_chunk' " +
                        "AND column_name = 'embedding'",
                String.class);

        assertThat(columnType).isNotNull();
        // MariaDB renders the vector type as "vector(1024)" in COLUMN_TYPE.
        assertThat(columnType.toLowerCase()).contains("vector(1024)");
    }

    @Test
    void chatConversationHasUniqueConstraintOnUuid() {
        Integer hits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name = 'chat_conversation' " +
                        "AND constraint_type = 'UNIQUE' " +
                        "AND constraint_name = 'uq_chat_conversation_uuid'",
                Integer.class);

        assertThat(hits).isEqualTo(1);
    }

    @Test
    void chatConversationHasIndexOnUpdatedAt() {
        Integer hits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name = 'chat_conversation' " +
                        "AND index_name = 'idx_chat_conversation_updated_at'",
                Integer.class);

        assertThat(hits).isGreaterThan(0);
    }

    private int countAppliedMigrations() {
        return flyway.info().applied().length;
    }
}
