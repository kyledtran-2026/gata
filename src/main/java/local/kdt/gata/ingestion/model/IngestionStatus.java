package local.kdt.gata.ingestion.model;

import jakarta.persistence.EnumeratedValue;
import jakarta.persistence.Table;

// Should match gata_ingest_status table entry
@Table(name="gata_ingest_status")
public enum IngestionStatus {
    INGESTED(1, "File ingested"),
    CREATED_S3_FOLDER(2, "Create S3 folder for document"),
    PARSED(3, "File has been parsed to its individual components"),
    CREATED_EMBEDDINGS(4, "Created embeddings"),
    CREATED_SUMMARIES(5, "Created summaries"),
    EXTRACTED_IMG_DESCR(6, "Extracted image description"),
    EXTRACTED_TABLES(7, "Extracted tables from text to DB"),
    COMPLETED(20,"PRAG data extraction completed"),
    FAILED(21,"Failed")
    ;

    @EnumeratedValue
    private final int id;
    private final String description;

    IngestionStatus(int id, String description) {
        this.id = id;
        this.description = description;
    }

    public int getId() { return id; }

    public String getDescription() {
        return description;
    }
}
