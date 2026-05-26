package ch.exmachina.cosmo42.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

public final class TestDbCleaner {

    private TestDbCleaner() {
    }

    public static void cleanChatTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM SPRING_AI_CHAT_MEMORY");
        jdbcTemplate.update("DELETE FROM chat_conversation");
    }
}
