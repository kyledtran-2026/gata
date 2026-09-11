package local.kdt.gata.ingestion.model;

import jakarta.persistence.EnumeratedValue;

public enum IngestSrc {
    MINIO(1, "Minio Bucket"),
    REST(2, "REST API")
    ;

    @EnumeratedValue
    private final int id;
    private final String description;

    IngestSrc(int id, String description) {
        this.id = id;
        this.description = description;
    }

    public int getId() { return id; }
    public String getDescription() { return description; }
}
