package local.kdt.gata.ingestion;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ingestion-service")
public class IngestionProperties {
    private String s3IngestFolder;
}
