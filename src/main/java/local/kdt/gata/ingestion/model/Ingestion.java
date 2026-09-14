package local.kdt.gata.ingestion.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "gata_ingestion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingestion {

    @Id
    @Column(name = "filename")
    private String filename;

    @NotNull
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status")
    private IngestionStatus status = IngestionStatus.INGESTED;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "ingest_src")
    private IngestSrc ingestSrc;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "s3_folder")
    private String s3Folder;

    @Column(name = "ingested_at")
    private Instant ingestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

}