package local.kdt.gata.minio;

import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.messages.NotificationRecords;
import io.minio.messages.NotificationRecords.Event;
import io.minio.messages.NotificationRecords;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import local.kdt.gata.appthread.AppThreadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

@Service
@EnableConfigurationProperties(MinIoProperties.class)
public class MinioService {
    private static final Logger LOG = LoggerFactory.getLogger(MinioService.class);

    private final static int FIXED_THREAD_POOL_SIZE = 20;
    private final static int SCHEDULED_THREAD_POOL_SIZE = 20;

    // This is used to hand off responsibility from the socket thread to this executor.
    private ThreadPoolExecutor threadPoolExecutor;
    // This is used when the connection is lost to schedule a delayed task to reconnect
    private ScheduledExecutorService scheduler;
    private MinIoProperties properties;
    private String bucket;
    private MinioClient minioClient;


    private Map<String, BucketNotificationListener> mapMinioListener;
    private Map<String, Future> mapBucketToFuture = new HashMap<>();
    private final AppThreadService threadPoolService;

    public MinioService(MinIoProperties properties, AppThreadService threadPoolService) {
        this.properties = properties;
        mapMinioListener = new HashMap<>();
        this.threadPoolService = threadPoolService;
    }

    @PostConstruct
    public void init() {
        if ( !isEnabled() )
            return;

        try {
            threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(FIXED_THREAD_POOL_SIZE);
            scheduler = new ScheduledThreadPoolExecutor(SCHEDULED_THREAD_POOL_SIZE);

            // check if bucket exist
            bucket = properties.getBucket();
            minioClient = getMinioClient();
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            startBucketListener(bucket);
        } catch (Exception ex) {
            LOG.error("init minio service failed", ex);
        }
    }

    public String registerListener(String folder, BucketNotificationListener listener) {
        if ( !isEnabled() )
            return null;

        try {
            if ( folder.endsWith("/") )
                folder = folder.substring(0, folder.length()-1);
            MinioClient minioClient = getMinioClient();
            String bucket = properties.getBucket();
            String placeHolderFile = ".placeholder_for_" + folder;
            if (!MinioUtil.objectExists(minioClient, bucket, folder, placeHolderFile)) {
                MinioUtil.putTextIntoBucketFolderFile(minioClient, bucket, folder, placeHolderFile, "");
            }
            mapMinioListener.put(MinioUtil.conditionFolder(folder), listener);
            return bucket;
        } catch (Exception ex) {
            LOG.error("registerListener failed", ex);
        }
        return null;
    }

    public String getBucket() {
        return properties.getBucket();
    }

    public MinioClient getMinioClient() {
        if ( minioClient==null ) {
            minioClient = MinioClient.builder().endpoint(properties.getMinioUrl())
                    .credentials(properties.getAccessKey(), properties.getSecretKey()).build();
        }
        return minioClient;
    }

    public InputStream getInputStream(String key) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(getBucket()).object(key).build());
    }

    public StatObjectResponse getObjectStat(String folder, String filename) {
        return MinioUtil.getObjectStat(minioClient, bucket, folder, filename);
    }

    public GetObjectResponse getObjectStream(String folder, String filename) throws MinioException {
        return MinioUtil.getObjectStream(minioClient, bucket, folder, filename);
    }

    public void removeObject(String key) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(getBucket()).object(key).build());
        LOG.info("removeObject remove "+key);
    }

    public void putObject(String folder, String filename, byte[] content) throws Exception {
        MinioUtil.putBytesIntoBucketFolder(minioClient, bucket, folder, filename, content);
    }

    public void putObject(String key, byte[] content) throws Exception {
        MinioUtil.putBytesIntoBucketFolder(minioClient, bucket, key, content);
    }

    public void putObject(String key, File file) throws Exception {
        MinioUtil.putFileIntoBucketFolder(minioClient, bucket, key, file);
    }

    public void putObject(String key, MultipartFile file) throws Exception {
        MinioUtil.putFileIntoBucketFolder(minioClient, bucket, key, file);
    }

    public void moveObject(String sourceKey, String destinationKey) throws Exception {
        MinioUtil.moveObject(minioClient, properties.getBucket(), sourceKey, destinationKey);
    }

    public void ensureFolder(String folder) throws Exception {
        MinioUtil.ensureFolder(minioClient, bucket, folder);
    }


    /**
     * Used to notify services that a file has been ingested on a new thread
     */
    class BucketNotificationTask implements Runnable {
        private final BucketNotificationListener bnl;
        private String folder;
        private String filename;
        private long objectSize;

        public BucketNotificationTask(BucketNotificationListener bnl, String folder, String filename, long objectSize) {
            this.bnl = bnl;
            this.folder = folder;
            this.filename = filename;
            this.objectSize = objectSize;
        }

        public void run() {
            String bucket = properties.getBucket();
            LOG.info("BucketNotificationTask folder={} name={} size={}", folder, filename, objectSize);
            bnl.minioBucketNotification(folder, filename, objectSize);
        }
    }

    /**
     * This is called when a file is placed in a MinIO bucket.  The code determines based on the path which equipment
     * should be notified through BucketNotificationTask
     */
    class BucketNotificationListenerTask implements Callable<String> {

        private String bucket;

        public BucketNotificationListenerTask(String bucket) {
            this.bucket = bucket;
        }

        @Override
        public String call() {
            LOG.info("BucketNotificationListenerTask.call");
            MinioClient mc = getMinioClient();
            String[] events = {"s3:ObjectCreated:*"};
            try (CloseableIterator<Result<NotificationRecords>> ci =
                         mc.listenBucketNotification(
                                 ListenBucketNotificationArgs.builder()
                                         .bucket(bucket)
                                         .events(events)
                                         .build())) {
                while (ci.hasNext()) {
                    NotificationRecords records = ci.next().get();
                    if (records.events() == null || records.events().isEmpty()) {
                        continue;
                    }

                    Event event = records.events().get(0);
                    if (event.object() == null || event.bucket() == null) {
                        continue;
                    }

                    long objectSize = event.object().size();
                    String key = event.object().key();

                    synchronized (this) {
                        LOG.info("BucketNotificationListenerTask bucket="+event.bucket().name()+" "+key);
                        int idx = key.lastIndexOf('/');
                        String folder = key.substring(0, idx+1);
                        String filename = key.substring(idx+1);
                        LOG.info("call folder="+folder+" "+mapMinioListener.keySet());

                        BucketNotificationListener listener = null;
                        if ( properties.isNotifyOnBaseFolder() ) {
                            int idx2 = folder.indexOf("/");
                            if ( idx2>0 && idx2!=idx ) {
                                String baseFolder = folder.substring(0, idx2+1);
                                listener = mapMinioListener.get(baseFolder);
                            }
                        }
                        if ( listener == null )
                            listener = mapMinioListener.get(folder);

                        if (listener != null) {
                            BucketNotificationTask bnt = new BucketNotificationTask(listener, folder, filename, objectSize);
                            threadPoolExecutor.submit(bnt);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("BucketNotificationListenerTask bucket="+bucket, e);
            } finally {
                LOG.info("BucketNotificationListenerTask wait 10 seconds and try to reconnect listener");
                Timer timer = new Timer("BucketNotificationListenerTaskRecovery");
                timer.schedule(new TimerTask() {
                    public void run() {
                        startBucketListener(bucket);
                    }
                }, 10000);
            }
            return bucket;
        }
    }

    // This starts the Minio bucket listener.
    private void startBucketListener(String bucket) {
        Future future = mapBucketToFuture.get(bucket);
        if ( future==null ) {
            LOG.info("startBucketListener="+bucket);
            BucketNotificationListenerTask bnlt = new BucketNotificationListenerTask(bucket);
            future = threadPoolService.submitTasks(bnlt);
            mapBucketToFuture.put(bucket, future);
        }
    }

    public boolean isEnabled() {
        String bucket = properties.getBucket();
        return ( !bucket.isBlank() );
    }

    public static String sanitizekey(String name) {
        if (name == null) return "";
        // Replace spaces with hyphen, then keep only safe characters
        return name
                .replace(" ", "-")                    // space → hyphen
                .replaceAll("[^a-zA-Z0-9_\\-./]", "_") // replace everything else with underscore
                .replaceAll("-+", "-")                // collapse multiple hyphens
                .replaceAll("_+", "_");               // collapse multiple underscores
    }

    @PreDestroy
    public void destroy() {
        LOG.info("shutting down executors");
        if ( scheduler!=null )
            scheduler.shutdown();
        if ( threadPoolExecutor!=null)
            threadPoolExecutor.shutdown();
    }
}
