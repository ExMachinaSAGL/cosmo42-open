CREATE TABLE kb_document_chunk (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    uuid char(36) NOT NULL,

    fk_kb_document_id bigint(20) NOT NULL,

    type varchar(16) NOT NULL,
    content TEXT NOT NULL,
    summary TEXT NULL DEFAULT NULL,
    embedding vector(1024) NOT NULL,

    PRIMARY KEY (id),
    FOREIGN KEY (fk_kb_document_id) REFERENCES kb_document (id)
);