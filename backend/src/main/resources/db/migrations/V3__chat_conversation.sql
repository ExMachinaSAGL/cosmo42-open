CREATE TABLE chat_conversation (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    uuid       CHAR(36)     NOT NULL,
    title      VARCHAR(255) NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_chat_conversation_uuid UNIQUE (uuid),
    INDEX idx_chat_conversation_updated_at (updated_at)
);

INSERT INTO chat_conversation (uuid, title, created_at, updated_at)
SELECT conversation_id, NULL, MIN(timestamp), MAX(timestamp)
FROM SPRING_AI_CHAT_MEMORY
GROUP BY conversation_id;
