package local.kdt.gata.ingestion;

import local.kdt.gata.common.threadpool.ThreadPoolSettings;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ingestion-service")
public class IngestionProperties {
    private String minioUploadFolder;
    private String minioOutputFolder;
}
