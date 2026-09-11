package local.kdt.gata.ingestion.model;

import jakarta.persistence.EnumeratedValue;

public enum IngestStatus {
    INGESTED(1, "File ingested"),
    PARSED(2, "File has been parsed to its individual components"),
    CREATED_EMBEDDINGS(3, "Created embeddings"),
    CREATED_SUMMARIES(4, "Created summaries"),
    EXTRACTED_TABLES(5, "Extracted tables from text to DB"),
    EXTRACTED_IMG_DESCR(5, "Extracted image description"),
    COMPLETED(10,"PRAG data extraction completed"),
    FAILED(11,"Failed")
    ;

    @EnumeratedValue
    private final int id;
    private final String description;

    IngestStatus(int id, String description) {
        this.id = id;
        this.description = description;
    }

    public int getId() { return id; }

    public String getDescription() {
        return description;
    }
}
