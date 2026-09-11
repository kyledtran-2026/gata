package local.kdt.gata.llm;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class LlmBaseProperties {
    private String url = "http://10.0.5.111:8789/v1/chat/completions";
    private String model = "gemma-4-31B-it-Q3_K_M";
    private double temperature = 0.0;
    private double topP = 0.9;
    private int maxTokens = 8192;
    private Integer seed;
    private String apiKey = "dummy";
    private Boolean logRequests = false;
    private Boolean logResponses = false;
}
