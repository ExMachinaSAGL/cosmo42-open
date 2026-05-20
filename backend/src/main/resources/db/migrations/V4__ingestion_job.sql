CREATE TABLE ingestion_job (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    uuid               CHAR(36)     NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_size_bytes    BIGINT       NOT NULL,
    stored_file_uuid   CHAR(36)     NULL,
    kb_document_uuid   CHAR(36)     NULL,
    total_pages        INT          NULL,
    chunks_embedded    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         DATETIME     NOT NULL,
    started_at         DATETIME     NULL,
    completed_at       DATETIME     NULL,
    error_message      TEXT         NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_ingestion_job_uuid UNIQUE (uuid)
);

CREATE INDEX idx_ingestion_job_status ON ingestion_job (status);

CREATE TABLE ingestion_job_page (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    fk_job_id     BIGINT       NOT NULL,
    page_index    INT          NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    attempt_count INT          NOT NULL DEFAULT 0,
    chunks_json   LONGTEXT     NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (fk_job_id) REFERENCES ingestion_job (id),
    CONSTRAINT uq_job_page UNIQUE (fk_job_id, page_index)
);
