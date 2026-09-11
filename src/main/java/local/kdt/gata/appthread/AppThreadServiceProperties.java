package local.kdt.gata.appthread;

import local.kdt.gata.common.threadpool.ThreadPoolSettings;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Property configuration loaded from ./resources/bootstrap.yml
 */
@Data
@ConfigurationProperties(prefix = "app-thread-service")
public class AppThreadServiceProperties extends ThreadPoolSettings {
}
