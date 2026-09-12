package local.kdt.gata.pipeline;

import com.google.common.eventbus.Subscribe;
import io.minio.StatObjectResponse;
import jakarta.annotation.PostConstruct;
import local.kdt.gata.common.threadpool.ThreadPoolFactory;
import local.kdt.gata.common.threadpool.ThreadPoolService;
import local.kdt.gata.common.util.FileUtil;
import local.kdt.gata.event.EventService;
import local.kdt.gata.ingestion.model.Ingest;
import local.kdt.gata.ingestion.model.IngestStatus;
import local.kdt.gata.minieru.MineruService;
import local.kdt.gata.minio.MinioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.*;

@Service
@EnableConfigurationProperties(PipelineProperties.class)
public class PipelineService {
    private static final Logger LOG = LoggerFactory.getLogger(PipelineService.class);

    private PipelineProperties properties;
    private final ThreadPoolService threadPoolService;
    private final EventService eventService;
    private final MinioService minioService;
    private final MineruService mineruService;

    public PipelineService(PipelineProperties properties, EventService eventService, MinioService minioService,
                           MineruService mineruService) {
        this.properties = properties;
        this.eventService = eventService;
        this.minioService = minioService;
        this.mineruService = mineruService;
        this.threadPoolService = ThreadPoolFactory.create(properties.getThreadPoolSettings());
    }

    @PostConstruct
    public void init() {
        eventService.register(this);
    }

    @Subscribe
    public void submitTask(Ingest ingest) {
        if ( ingest.getIngestStatus()!= IngestStatus.INGESTED )
            return;
        LOG.info("submitTask {}", ingest.getFilename());
        threadPoolService.submitTasks(new PipelineTask(ingest));
    }

    class PipelineTask implements Callable {
        private Ingest ingest;

        public PipelineTask(Ingest ingest) {
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
                    String key = ingest.getS3Folder()+filenameNoExt+".zip";
                    LOG.info("call mineru to minio {}", key);
                    minioService.putObject(key, zipBytes);
                }
            } catch (Exception ex) {
                LOG.error("call processing failed for {}", filename, ex);
            }
            return null;
        }

        private static String text(JsonNode n, String field) {
            JsonNode v = n.get(field);
            return v == null || v.isNull() ? null : v.asText();
        }
    }
}
