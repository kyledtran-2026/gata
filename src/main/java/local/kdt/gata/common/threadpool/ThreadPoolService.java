package local.kdt.gata.common.threadpool;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * ThreadPool for asynchronous tasks.
 */
public class ThreadPoolService {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolService.class);

    private ThreadPoolTaskExecutor taskExecutor;

    public ThreadPoolService(ThreadPoolSettings properties) {
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(properties.getCorePoolSize());
        taskExecutor.setMaxPoolSize(properties.getMaxPoolSize());
        taskExecutor.setQueueCapacity(properties.getQueueCapacity());
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.setAwaitTerminationSeconds(properties.getAwaitTerminationInSec());
        taskExecutor.initialize();
        LOG.info("ThreadPoolServiceImpl properties=" + properties);
    }

    public void shutdown() {
        if ( taskExecutor!=null )
            taskExecutor.shutdown();
    }

    /**
     * Submit task to the thread executor.
     *
     * @param task
     * @return Future to obtain a handle on the tasks.
     */
    public Future<?> submitTasks(Callable<String> task) {
        return taskExecutor.submit(task);
    }

    public int getActiveCount() {
        return taskExecutor.getActiveCount();
    }

    public int getRemainingQueueCapacity() {
        return taskExecutor.getQueueCapacity()-taskExecutor.getQueueSize();
    }

    @PreDestroy
    public void destroy() {
        if ( taskExecutor!=null)
            taskExecutor.shutdown();
    }
}
