package local.kdt.gata.llm;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "llm.vision")
public class LlmVisionProperties extends LlmBaseProperties{

    private int dpi = 150;

    public void setDpi(int dpi) { this.dpi = dpi; }
}
