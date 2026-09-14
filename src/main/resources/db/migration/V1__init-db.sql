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
    VALUES (1, 'INGESTED', 'File ingested to S3'),
        (2, 'CREATED_S3_FOLDER', 'Create S3 folder for document'),
        (3, 'PARSED', 'Retrieve MinerU artifacts'),
        (4, 'CREATED_EMBEDDINGS', 'Create embeddings'),
        (5, 'CREATED_SUMMARIES', 'Create summaries'),
        (6, 'CREATE_IMG_DESCR', 'Create image description'),
        (7, 'EXTRACTED_TABLES', 'Extract tables from text and store into DB'),
        (20, 'COMPLETED', 'RAG data extraction completed'),
        (21, 'FAILED', 'RAG data extraction failed');

CREATE TABLE gata_ingest_src (
    id          SMALLINT PRIMARY KEY,
    name        VARCHAR(32),
    description TEXT
);

INSERT INTO gata_ingest_src(id, name, description)
VALUES (1, 'MINIO', 'File from Minio'),
       (2, 'REST', 'File from REST');

CREATE TABLE gata_ingestion (
    filename            TEXT PRIMARY KEY,
    status              SMALLINT DEFAULT 1 REFERENCES gata_ingest_status(id) ON DELETE CASCADE,
    ingest_src          SMALLINT DEFAULT 1 REFERENCES gata_ingest_src(id) ON DELETE CASCADE,
    failure_reason      TEXT,
    s3_folder           TEXT,
    ingested_at         TIMESTAMPTZ DEFAULT now(),
    completed_at        TIMESTAMPTZ
);

COMMENT ON TABLE gata_ingestion IS 'Central registry of all ingested documents';
COMMENT ON COLUMN gata_ingestion.filename IS 'Filename of document during ingestion';
COMMENT ON COLUMN gata_ingestion.status IS 'The process status of the document';
COMMENT ON COLUMN gata_ingestion.failure_reason IS 'The failure reason why the document was not fully processed';
COMMENT ON COLUMN gata_ingestion.s3_folder IS 'The path/folder in the S3/Minio where the file is kept';
COMMENT ON COLUMN gata_ingestion.ingested_at IS 'When the file was ingested';
COMMENT ON COLUMN gata_ingestion.completed_at IS 'When the pipeline process completed/failed';