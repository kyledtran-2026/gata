package local.kdt.gata.common.threadpool;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ThreadPoolSettings {
    private int corePoolSize = 10;
    private int maxPoolSize = 20;
    private int queueCapacity = 100;
    private int awaitTerminationInSec = 60;
}