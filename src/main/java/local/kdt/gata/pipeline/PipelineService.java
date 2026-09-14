package local.kdt.gata.pipeline;

import com.google.common.eventbus.Subscribe;
import io.minio.StatObjectResponse;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import local.kdt.gata.common.threadpool.ThreadPoolFactory;
import local.kdt.gata.common.threadpool.ThreadPoolService;
import local.kdt.gata.common.util.FileUtil;
import local.kdt.gata.common.util.ZipBytesUtil;
import local.kdt.gata.event.EventService;
import local.kdt.gata.ingestion.IngestionService;
import local.kdt.gata.minio.MinioUtil;
import local.kdt.gata.minio.S3ObjectKeys;
import local.kdt.gata.ingestion.model.IngestSrc;
import local.kdt.gata.ingestion.model.Ingestion;
import local.kdt.gata.ingestion.model.IngestionStatus;
import local.kdt.gata.minieru.MineruService;
import local.kdt.gata.minio.MinioService;
import local.kdt.gata.ingestion.model.IngestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Service
@EnableConfigurationProperties(PipelineProperties.class)
public class PipelineService {
    private static final Logger LOG = LoggerFactory.getLogger(PipelineService.class);

    private final PipelineProperties properties;
    private final IngestionService ingestionService;
    private final ThreadPoolService threadPoolService;
    private final EventService eventService;
    private final MinioService minioService;
    private final MineruService mineruService;

    public PipelineService(PipelineProperties properties, IngestionService ingestionService, EventService eventService,
                           MinioService minioService, MineruService mineruService) {
        this.properties = properties;
        this.ingestionService = ingestionService;
        this.eventService = eventService;
        this.minioService = minioService;
        this.mineruService = mineruService;
        this.properties.setS3RagFolder(MinioUtil.conditionFolder(properties.getS3RagFolder()));
        this.threadPoolService = ThreadPoolFactory.create(properties.getThreadPoolSettings());
    }

    @PostConstruct
    public void init() {
        if ( minioService.isEnabled() ) {
            try {
                minioService.ensureFolder(properties.getS3RagFolder());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize MinIO bucket/folders", e);
            }
        }
        eventService.register(this);
    }

    @Subscribe
    public void submitTask(Ingestion ingest) {
        if ( ingest.getStatus()!= IngestionStatus.INGESTED )
            return;
        String filename = ingest.getFilename();
        LOG.info("submitTask {}", filename);
        Ingestion dbIngest = ingestionService.getIngestByFilename(filename);
        try {
            if (dbIngest != null) {
                // delete existing entries and start over
                if (Duration.between(dbIngest.getIngestedAt(), Instant.now()).getSeconds()>2 ) {
                    LOG.info("minioBucketNotification delete previous entry: {}", filename);
                    ingestionService.deleteIngest(filename);
                }
            }

            String directory = FileUtil.getFileNameWithoutExtension(S3ObjectKeys.toS3SafeFilename(filename));
            directory = properties.getS3RagFolder()+directory+"/";
            String key = directory + filename;
            minioService.moveObject(ingest.getS3Folder()+"/"+filename, key);
            ingest.setS3Folder(directory);
            ingest.setStatus(IngestionStatus.CREATED_S3_FOLDER);
            ingestionService.updateIngest(ingest);
            threadPoolService.submitTasks(new PipelineTask(ingest));
        } catch(Exception ex) {
            LOG.error("submitTask", ex);
            ingest.setStatus(IngestionStatus.FAILED);
            ingest.setFailureReason(ex.getMessage());
            ingest.setCompletedAt(Instant.now());
            ingestionService.updateIngest(ingest);
        }

    }

    private static void unzipIntoMinio(MinioService minioService, @NotNull String destFolder, byte[] zipBytes, String rmBasePath)
            throws Exception {
        Map<String, byte[]> files = ZipBytesUtil.unzip(zipBytes);
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String zipEntryPath = e.getKey().replace(rmBasePath, "");
            String key = destFolder + zipEntryPath;
            String safeKey = MinioService.sanitizekey(key);
            minioService.putObject(safeKey, e.getValue());
        }
    }

    class PipelineTask implements Callable {
        private Ingestion ingest;

        public PipelineTask(Ingestion ingest) {
            this.ingest = ingest;
        }

        @Override
        public Object call() throws Exception {
            String filename = ingest.getFilename();;
            try {
                LOG.info("call start processing {}", filename);
                StatObjectResponse stat = minioService.getObjectStat(ingest.getS3Folder(), filename);
                InputStream inputStream = minioService.getObjectStream(ingest.getS3Folder(), filename);
                String taskId = mineruService.asyncSubmit(inputStream, filename, stat.contentType(), stat.size());
                String status = mineruService.waitUntilCompleted(taskId);
                if ( status.equals("completed")) {
                    LOG.info("call mineru completed {}", filename);
                    byte[] zipBytes = mineruService.resultZip(taskId);
                    String filenameNoExt = FileUtil.getFileNameWithoutExtension(filename);
                    String removeBasePath = filenameNoExt + "/auto/";
                    // Debug to output the zip file
//                    String key = ingest.getS3Folder()+filenameNoExt+".zip";
//                    LOG.info("call mineru to minio {}", key);
//                    minioService.putObject(key, zipBytes);
                    unzipIntoMinio(minioService, ingest.getS3Folder(), zipBytes, removeBasePath);
                    ingest.setStatus(IngestionStatus.PARSED);
                    ingestionService.updateIngest(ingest);
                }
            } catch (Exception ex) {
                LOG.error("call processing failed for {}", filename, ex);
            }
            return null;
        }
    }


}
