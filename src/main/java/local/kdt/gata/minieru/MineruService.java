package local.kdt.gata.minieru;

import local.kdt.gata.common.util.ContentTypeUtil;
import local.kdt.gata.common.util.JsonUtil;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

    public MineruService(MineruProperties properties,
                         RestClient.Builder restClientBuilder) {
        this.properties = properties;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(60)); // POST /tasks should return immediately
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    public String asyncSubmit(InputStream inputStream, String filename,
                              String contentType, long contentLength) throws Exception {
        LOG.info("asyncSubmit {}", filename);

        if (contentType == null || contentType.isBlank()) {
            contentType = ContentTypeUtil.fromFilename(filename);
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/pdf";
        }

        byte[] fileBytes;
        try (inputStream) {
            fileBytes = inputStream.readAllBytes();
        }
        LOG.info("asyncSubmit {} read {} bytes (stat size={})", filename, fileBytes.length, contentLength);

        HttpEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.STRICT)
                .addBinaryBody("files", fileBytes, ContentType.parse(contentType), filename)
                .addTextBody("return_md", "true", ContentType.TEXT_PLAIN)
                .build();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        entity.writeTo(buf);
        byte[] body = buf.toByteArray();

        String rawContentType = entity.getContentType();
        String boundary = null;
        for (String part : rawContentType.split(";")) {
            String p = part.trim();
            if (p.regionMatches(true, 0, "boundary=", 0, 9)) {
                boundary = p.substring(9).trim().replace("\"", "");
            }
        }
        if (boundary == null || boundary.isBlank()) {
            throw new IllegalStateException("no multipart boundary in " + rawContentType);
        }
        String contentTypeHeader = "multipart/form-data; boundary=" + boundary;

        String url = properties.getBaseUrl().replaceAll("/$", "") + "/tasks";

        int previewLen = Math.min(body.length, 300);
        String head = new String(body, 0, previewLen, StandardCharsets.ISO_8859_1);
        String tail = new String(body, Math.max(0, body.length - 200), Math.min(200, body.length), StandardCharsets.ISO_8859_1);
        LOG.info("asyncSubmit POST {} contentType={} bodyBytes={}", url, contentTypeHeader, body.length);
        LOG.info("asyncSubmit head:\n{}", head.replace("\r", "\\r").replace("\n", "\\n\n"));
        LOG.info("asyncSubmit tail:\n{}", tail.replace("\r", "\\r").replace("\n", "\\n\n"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", contentTypeHeader)
                .header("Accept", "application/json")
                .header("User-Agent", "curl/8.5.0")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        LOG.info("asyncSubmit status={} httpVersion={} body={}",
                response.statusCode(), response.version(), response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("mineru /tasks " + response.statusCode() + " " + response.body());
        }

        String taskId = mapper.readTree(response.body()).path("task_id").asText(null);
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalStateException("no task_id: " + response.body());
        }
        return taskId;
    }

    public String asyncStatus(String taskId) {
        LOG.info("waitUntilCompleted {}", taskId);
        JsonNode payload = restClient.get()
                .uri("/tasks/{id}", taskId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new IllegalStateException("task missing: " + taskId);
                })
                .body(JsonNode.class);
        String status = text(payload, "status");
        LOG.info("waitUntilCompleted {} status={}", taskId, status);
        return status;
    }

    public String waitUntilCompleted(String taskId) throws InterruptedException {
        Duration timeout = Duration.ofSeconds(properties.getPollTimeout());
        long deadline = System.nanoTime() + timeout.toNanos();
        LOG.info("waitUntilCompleted {}", taskId);
        long poolPeriod = properties.getPollInterval() * 1000;
        int cnt = 0;
        while (true) {
            String status = asyncStatus(taskId);
            LOG.info("waitUntilCompleted {} {}", cnt++, status);

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

    public byte[] submitSyncMinioFileToMineru(InputStream inputStream, String filename, String contentType,
                                              long contentLength) throws Exception {
        LOG.info("submitMinioFile {}", filename);
        byte[] zipBytes = sendFile("/file_parse", inputStream, filename, contentType, contentLength, byte[].class, HttpStatusCode::isError);
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalStateException("mineru /file_parse returned empty body");
        }
        return zipBytes;
    }

    private <T> T sendFile(String uri, InputStream inputStream, String filename, String contentType, long contentLength,
                           Class<T> responseType, Predicate<HttpStatusCode> errorWhen) throws Exception {

        String resolvedContentType = contentType != null
                ? contentType
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        try (inputStream) {
            var resource = new InputStreamResource(inputStream) {
                @Override public String getFilename() { return filename; }
                @Override public long contentLength() { return contentLength; }
            };

            return restClient.post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parseBody(resource, filename, resolvedContentType))
                    .retrieve()
                    .onStatus(errorWhen, (req, res) -> {
                        throw new IllegalStateException(
                                "mineru " + uri + " failed: " + res.getStatusCode());
                    })
                    .body(responseType);
        }
    }

    private MultiValueMap<String, Object> parseBody(Resource file, String filename, String contentType) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentDisposition(
                ContentDisposition.formData().name("files").filename(filename).build());
        if (contentType != null && !contentType.isBlank()) {
            fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        }
        parts.add("files", new org.springframework.http.HttpEntity<>(file, fileHeaders));

        parseOptions().forEach(parts::add);
        return parts;
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