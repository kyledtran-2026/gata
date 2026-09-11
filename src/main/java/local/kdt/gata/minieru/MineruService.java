package local.kdt.gata.minieru;

import local.kdt.gata.common.util.FileUtil;
import local.kdt.gata.common.util.JsonUtil;
import local.kdt.gata.minio.MinioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;

@Service
@EnableConfigurationProperties(MineruProperties.class)
public class MineruService {
    private static final Logger LOG = LoggerFactory.getLogger(MineruService.class);
    private static final JsonMapper mapper = JsonUtil.getJsonMapper();

    private final MineruProperties properties;
    private final RestClient restClient;
    private final MinioService minioService;

    public MineruService(MineruProperties properties,
                         MinioService minioService,
                         RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.minioService = minioService;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    public String asyncSubmit(String folder, String filename) throws Exception {
        LOG.info("asyncSubmit {}", filename);
        String json = sendMinioFile("/tasks", folder, filename, String.class, status ->
                status.value() != 202 && !status.is2xxSuccessful());
        String taskId = mapper.readTree(json).path("task_id").asText(null);
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalStateException("no task_id: " + json);
        }
        return taskId;
    }

    public String asyncStatus(String taskId) {
        JsonNode payload = restClient.get()
                .uri("/tasks/{id}", taskId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new IllegalStateException("task missing: " + taskId);
                })
                .body(JsonNode.class);
        String status = text(payload, "status");
        return status;
    }

    public String waitUntilCompleted(String taskId) throws InterruptedException {
        Duration timeout = Duration.ofSeconds(properties.getPollTimeout());
        long deadline = System.nanoTime() + timeout.toNanos();

        long poolPeriod = properties.getPollInterval() * 1000;
        while (true) {
            String status = asyncStatus(taskId);

            if ("completed".equals(status)) {
                return status;
            }
            if ("failed".equals(status)) {
                throw new IllegalStateException("MinerU task failed: " + taskId);
            }
            if (!"pending".equals(status) && !"processing".equals(status)) {
                throw new IllegalStateException(
                        "MinerU unexpected status '" + status + "' for task " + taskId);
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException(
                        "MinerU task timed out after " + timeout + ": " + taskId);
            }
            Thread.sleep(poolPeriod);
        }
    }

    public byte[] resultZip(String taskId) {
        return restClient.get()
                .uri("/tasks/{id}/result", taskId)
                .retrieve()
                .onStatus(s -> s.value() == 202,
                        (req, res) -> { throw new MineruNotReadyException(taskId); })
                .onStatus(s -> s.value() == 409,
                        (req, res) -> { throw new IllegalStateException("parse failed: " + taskId); })
                .onStatus(s -> s.value() == 404,
                        (req, res) -> { throw new IllegalStateException("cleaned up: " + taskId); })
                .body(byte[].class);
    }

    public String submitSyncMinioFileToMineru(String folder, String filename) throws Exception {
        LOG.info("submitMinioFile {}", filename);
        byte[] zipBytes = sendMinioFile("/file_parse", folder, filename, byte[].class, HttpStatusCode::isError);
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalStateException("mineru /file_parse returned empty body");
        }
        String zipFile = FileUtil.getFileNameWithoutExtension(filename) + "_mineru.zip";
        minioService.putObject(folder, zipFile, zipBytes);
        LOG.info("submitMinioFile output {} {}", zipFile, zipBytes.length);
        return zipFile;
    }

    private <T> T sendMinioFile(String uri,
                                String folder,
                                String filename,
                                Class<T> responseType,
                                Predicate<HttpStatusCode> errorWhen) throws Exception {
        var stat = minioService.getObjectStat(folder, filename);
        String contentType = stat.contentType() != null
                ? stat.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        try (var objectStream = minioService.getObjectStream(folder, filename)) {
            var resource = new InputStreamResource(objectStream) {
                @Override public String getFilename() { return filename; }
                @Override public long contentLength() { return stat.size(); }
            };

            return restClient.post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parseBody(resource, filename, contentType))
                    .retrieve()
                    .onStatus(errorWhen, (req, res) -> {
                        throw new IllegalStateException(
                                "mineru " + uri + " failed: " + res.getStatusCode());
                    })
                    .body(responseType);
        }
    }

    private byte[] postZip(String uri, MultiValueMap<String, HttpEntity<?>> body) {
        byte[] zip = restClient.post()
                .uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new IllegalStateException(
                            "mineru " + uri + " failed: " + res.getStatusCode());
                })
                .body(byte[].class);

        if (zip == null || zip.length == 0) {
            throw new IllegalStateException("mineru " + uri + " returned empty body");
        }
        return zip;
    }

    private MultiValueMap<String, HttpEntity<?>> parseBody(Resource file, String filename, String contentType) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        var filePart = body.part("files", file).filename(filename);
        if (contentType != null && !contentType.isBlank()) {
            filePart.contentType(MediaType.parseMediaType(contentType));
        }

        parseOptions().forEach(body::part);
        return body.build();
    }

    private Map<String, String> parseOptions() {
        return Map.of(
                "response_format_zip", "true",
                "return_md", "true",
                "return_images", "true",
                "return_middle_json", "false",
                "return_model_output", "false",
                "return_content_list", "false",
                "return_original_file", "false",
                "lang_list", "en",
                "parse_method", "auto",
                "backend", "pipeline"
        );
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    public static class MineruNotReadyException extends RuntimeException {
        public MineruNotReadyException(String taskId) {
            super("MinerU result not ready: " + taskId);
        }
    }
}