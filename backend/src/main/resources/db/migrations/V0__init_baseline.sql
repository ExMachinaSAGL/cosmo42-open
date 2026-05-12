CREATE TABLE kb_document (
   id                  BIGINT          NOT NULL AUTO_INCREMENT,
   uuid                CHAR(36)        NOT NULL,
   file_name           VARCHAR(255)    NOT NULL,
   file_size           BIGINT          NOT NULL,
   creation_timestamp  DATETIME        NOT NULL,
   PRIMARY KEY (id),
   CONSTRAINT uq_audit_uploads_uuid UNIQUE (uuid)
);
