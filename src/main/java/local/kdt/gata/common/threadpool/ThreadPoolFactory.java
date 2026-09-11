package local.kdt.gata.common.threadpool;

import org.springframework.stereotype.Component;

@Component
public class ThreadPoolFactory {
    public static ThreadPoolService create(ThreadPoolSettings properties) {
        return new ThreadPoolService(properties);
    }
}
