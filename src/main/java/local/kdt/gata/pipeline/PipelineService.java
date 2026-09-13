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
import local.kdt.gata.ingestion.model.Ingest;
import local.kdt.gata.ingestion.model.IngestStatus;
import local.kdt.gata.minieru.MineruService;
import local.kdt.gata.minio.MinioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;
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

    public static void unzipIntoMinio(MinioService minioService, @NotNull String destFolder, byte[] zipBytes, String rmBasePath)
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
                    String removeBasePath = filenameNoExt + "/auto/";
                    // Debug to output the zip file
//                    String key = ingest.getS3Folder()+filenameNoExt+".zip";
//                    LOG.info("call mineru to minio {}", key);
//                    minioService.putObject(key, zipBytes);
                    unzipIntoMinio(minioService, ingest.getS3Folder(), zipBytes, removeBasePath);
                }
            } catch (Exception ex) {
                LOG.error("call processing failed for {}", filename, ex);
            }
            return null;
        }
    }


}
