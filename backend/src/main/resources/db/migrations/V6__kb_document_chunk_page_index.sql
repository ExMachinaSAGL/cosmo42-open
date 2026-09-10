ALTER TABLE kb_document_chunk
	ADD COLUMN start_page integer NOT NULL DEFAULT -1,
	ADD COLUMN end_page integer NOT NULL DEFAULT -1;

ALTER TABLE kb_document_chunk
	ALTER COLUMN start_page DROP DEFAULT,
	ALTER COLUMN end_page DROP DEFAULT;
