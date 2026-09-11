package local.kdt.gata.llm;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;


/**
 * This is primarily for testing to verify communication between application and llm-server.  This can be removed in
 * production.
 */
@RestController
@RequestMapping("/api/llm")
public class LlmController {


    private static final Logger LOG = LoggerFactory.getLogger(LlmController.class);


    private final LlmService llmService;


    public LlmController(LlmService llmService) {
        this.llmService = llmService;
    }


    // ==================== TEXT QUERY ====================
    record ChatRequest(String systemPrompt, String userMessage) {}
    record ChatResponse(String result, long durationMs, String status) {}
    record ErrorResponse(String error, long durationMs, String operation) {}


    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest req) {
        Instant start = Instant.now();
        try {
            String sys = (req.systemPrompt() != null && !req.systemPrompt().isBlank())
                    ? req.systemPrompt() : "You are a helpful assistant.";
            String usr = (req.userMessage() != null && !req.userMessage().isBlank())
                    ? req.userMessage() : "Hello, please confirm you are responding from the gemma-4-31B vision model.";


            String result = llmService.chat(sys, usr);
            long ms = Duration.between(start, Instant.now()).toMillis();
            LOG.info("chat succeeded in {} ms", ms);
            return ResponseEntity.ok(new ChatResponse(result, ms, "success"));
        } catch (Exception e) {
            long ms = Duration.between(start, Instant.now()).toMillis();
            LOG.error("chat failed after {} ms", ms, e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getClass().getSimpleName() + ": " + e.getMessage(), ms, "chat"));
        }
    }


    // ==================== PDF ====================
    record PdfResponse(List<String> pages, long durationMs, String status, String filename) {}


    @PostMapping(value = "/analyze-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyzePdf(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "systemPrompt", required = false) String systemPrompt,
            @RequestParam(value = "userPrompt", required = false) String userPrompt) {


        Instant start = Instant.now();
        String fname = file.getOriginalFilename();
        try {
            String sys = (systemPrompt != null && !systemPrompt.isBlank())
                    ? systemPrompt : "You are an expert technical document analyst. Be precise and structured.";
            String usr = (userPrompt != null && !userPrompt.isBlank())
                    ? userPrompt : "Analyze this PDF page-by-page. Extract all tables, KPIs, numbers, dates, and key findings. Output clearly labeled per page.";


            LOG.info("analyzePdf starting for {} ({} bytes)", fname, file.getSize());


            try (InputStream is = file.getInputStream()) {
                List<String> pages = llmService.analyzePdf(is, sys, usr);
                long ms = Duration.between(start, Instant.now()).toMillis();
                LOG.info("analyzePdf succeeded for {} in {} ms ({} pages)", fname, ms, pages.size());
                return ResponseEntity.ok(new PdfResponse(pages, ms, "success", fname));
            }
        } catch (Exception e) {
            long ms = Duration.between(start, Instant.now()).toMillis();
            LOG.error("analyzePdf failed for {} after {} ms", fname, ms, e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getClass().getSimpleName() + ": " + e.getMessage(), ms, "analyze-pdf"));
        }
    }


    // ==================== IMAGE ====================
    record ImageResponse(String result, long durationMs, String status, String filename, String mimeType) {}

    @PostMapping(value = "/analyze-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyzeImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "systemPrompt", required = false) String systemPrompt,
            @RequestParam(value = "userPrompt", required = false) String userPrompt) {


        Instant start = Instant.now();
        String fname = file.getOriginalFilename();
        try {
            String sys = (systemPrompt != null && !systemPrompt.isBlank())
                    ? systemPrompt : "You are an expert image analyst.";
            String usr = (userPrompt != null && !userPrompt.isBlank())
                    ? userPrompt : "Describe this image in detail. Extract any text, tables, numbers, or technical content visible.";


            String mimeType = determineMimeType(file);
            LOG.info("analyzeImage starting for {} ({} bytes, mime={})", fname, file.getSize(), mimeType);


            try (InputStream is = file.getInputStream()) {
                String result = llmService.analyzeImage(is, mimeType, sys, usr);
                long ms = Duration.between(start, Instant.now()).toMillis();
                LOG.info("analyzeImage succeeded for {} in {} ms", fname, ms);
                return ResponseEntity.ok(new ImageResponse(result, ms, "success", fname, mimeType));
            }
        } catch (Exception e) {
            long ms = Duration.between(start, Instant.now()).toMillis();
            LOG.error("analyzeImage failed for {} after {} ms", fname, ms, e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getClass().getSimpleName() + ": " + e.getMessage(), ms, "analyze-image"));
        }
    }


    private String determineMimeType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && ct.startsWith("image/")) return ct;
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
            return switch (ext) {
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                case "bmp" -> "image/bmp";
                default -> "image/png";
            };
        }
        return "image/png";
    }


    // Quick health / model check
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok("LlmTestController ready. Use /chat, /analyze-pdf, /analyze-image");
    }
}
