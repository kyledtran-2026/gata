-- ============================================================
-- GATA Document Schema
-- ============================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;

-- ------------------------------------------------------------
-- Drop tables (development convenience - safe to re-run)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS gata_ingest CASCADE;
DROP TABLE IF EXISTS gata_ingest_status CASCADE;
DROP TABLE IF EXISTS gata_ingest_src CASCADE;

CREATE TABLE gata_ingest_status (
    id          SMALLINT PRIMARY KEY,
    name        VARCHAR(32),
    description TEXT
);

INSERT INTO gata_ingest_status(id, name, description)
    VALUES (1, 'INGESTED', 'File ingested'),
        (2, 'PARSED', 'File has been parsed to its individual components'),
        (3, 'CREATED_EMBEDDINGS', 'Created embeddings'),
        (4, 'CREATED_SUMMARIES', 'Created summary'),
        (5, 'EXTRACTED_TABLES', 'Extracted tables from text to DB'),
        (6, 'EXTRACTED_IMG_DESCR', 'Extracted image description'),
        (10, 'COMPLETED', 'RAG data extraction completed'),
        (11, 'FAILED', 'RAG data extraction failed');

CREATE TABLE gata_ingest_src (
    id          SMALLINT PRIMARY KEY,
    name        VARCHAR(32),
    description TEXT
);

INSERT INTO gata_ingest_src(id, name, description)
VALUES (1, 'MINIO', 'File from Minio'),
       (2, 'REST', 'File from REST');

CREATE TABLE gata_ingest (
    filename            TEXT PRIMARY KEY,
    ingest_status       SMALLINT DEFAULT 1 REFERENCES gata_ingest_status(id) ON DELETE CASCADE,
    ingest_src          SMALLINT DEFAULT 1 REFERENCES gata_ingest_src(id) ON DELETE CASCADE,
    failure_reason      TEXT,
    s3_folder           TEXT,
    ingested_at         TIMESTAMPTZ DEFAULT now(),
    completed_at        TIMESTAMPTZ
);

COMMENT ON TABLE gata_ingest IS 'Central registry of all ingested documents';
COMMENT ON COLUMN gata_ingest.filename IS 'Filename of document during ingestion';
COMMENT ON COLUMN gata_ingest.ingest_status IS 'The process status of the document';
COMMENT ON COLUMN gata_ingest.failure_reason IS 'The failure reason why the document was not fully processed';
COMMENT ON COLUMN gata_ingest.s3_folder IS 'The path/folder in the S3/Minio where the file is kept';
COMMENT ON COLUMN gata_ingest.ingested_at IS 'When the file was ingested';
COMMENT ON COLUMN gata_ingest.completed_at IS 'When the pipeline process completed/failed';