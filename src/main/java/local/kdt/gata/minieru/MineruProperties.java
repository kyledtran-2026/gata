package local.kdt.gata.minieru;

import local.kdt.gata.common.threadpool.ThreadPoolSettings;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "mineru-service")
public class MineruProperties {
    private String baseUrl;
    private int connectTimeout = 10;        // in seconds
    private int readTimeout = 180;          // in seconds
    private int pollInterval = 5;           // in seconds
    private int pollTimeout = 3600;         // in seconds
    private String defaultLang = "en";
    private String defaultBackend = "pipeline";
    private String defaultParseMethod = "auto";
}