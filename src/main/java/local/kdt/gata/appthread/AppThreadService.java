package local.kdt.gata.appthread;

import local.kdt.gata.common.threadpool.ThreadPoolFactory;
import local.kdt.gata.common.threadpool.ThreadPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

@Service
@EnableConfigurationProperties(AppThreadServiceProperties.class)
public class AppThreadService {
    private static final Logger LOG = LoggerFactory.getLogger(AppThreadService.class);

    private final ThreadPoolService threadPool;

    public AppThreadService(ThreadPoolFactory threadPoolFactory, AppThreadServiceProperties properities) {
        this.threadPool = threadPoolFactory.create(properities);
    }

    public ThreadPoolService getThreadPoolService() {
        return threadPool;
    }

    public Future<?> submitTasks(Callable<String> task) {
        return threadPool.submitTasks(task);
    }

}