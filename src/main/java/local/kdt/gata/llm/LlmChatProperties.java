package local.kdt.gata.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.chat")
public class LlmChatProperties extends LlmBaseProperties {
}
