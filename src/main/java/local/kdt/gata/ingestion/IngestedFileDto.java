package local.kdt.gata.ingestion;

import jakarta.validation.constraints.NotNull;
import local.kdt.gata.ingestion.model.IngestSrc;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestedFileDto {

    @NotNull
    private Boolean overwrite;
    private String filename;
    private Instant ingestionDate;
    private IngestSrc ingestSrc;
    private String filePath;
    private long size;
    private Map<String, Object> metadata;

}