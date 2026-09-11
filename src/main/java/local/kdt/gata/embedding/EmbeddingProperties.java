package local.kdt.gata.embedding;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "llm.embedding")
public class EmbeddingProperties {

    // getters and setters
    private String url;
    private String model;
    private Integer dimensions;        // optional, very useful
    private Integer maxRetries = 3;
    private String apiKey = "dummy";

    private Boolean logRequests = false;
    private Boolean logResponses = false;
}