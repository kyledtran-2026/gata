package local.kdt.gata.pipeline;

import local.kdt.gata.common.threadpool.ThreadPoolSettings;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pipeline-service")
public class PipelineProperties {
    private String s3RagFolder;
    private ThreadPoolSettings threadPoolSettings;
}
