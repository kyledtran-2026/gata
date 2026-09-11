package local.kdt.gata.llm;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.validation.constraints.NotNull;
import local.kdt.gata.common.util.PdfImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

@Service
@EnableConfigurationProperties({ LlmChatProperties.class, LlmVisionProperties.class })
public class LlmService {

    private static final Logger LOG = LoggerFactory.getLogger(LlmService.class);

    private final LlmVisionProperties visionProperties;
    private final ChatModel chatModel;   // text-only
    private final ChatModel visionModel; // multimodal / vision

    private final String SYSTEM_PROMPT_SUMMARIZE_CONTENT =
        """
        You are an expert technical document analyst creating dense, factual summaries optimized for vector embeddings in a RAG system.
        
        Strict rules:
        - Output ONLY the summary. Never add explanations, introductions, or meta text such as "Here is a summary".
        - Keep it extremely concise: 1–2 sentences is ideal. Maximum 3 sentences.
        - Preserve every critical fact: key entities, numbers, dates, technical terms, results, decisions, and actions.
        - Be completely faithful to the source. Do not add, infer, or hallucinate information.
        - For tables, structured data, or lists, capture the purpose and key insights rather than repeating every row.
        - Use precise, information-dense language that works well for semantic embeddings.
        - If the content is very short or already concise, you may return it nearly unchanged.            
        """;

    private final String USER_PROMPT_SUMMARIZE_CONTENT =
        """
        Document context:
        - Content type: {{type}}          (e.g. text, title, table, formula, etc.)
        - Page number: {{page_idx}}
        - Section / Heading: {{section_title}}   (if known, otherwise omit this line)
        
        Content to summarize:
        {{content}}
        
        Task: Write a concise, information-dense summary of the content above. The summary will be used to create a vector embedding for semantic retrieval.
        """;

    public LlmService(LlmChatProperties chatProperties,
                      LlmVisionProperties visionProperties) {

        // Text-only model (your original behavior)
        this.chatModel = OpenAiChatModel.builder()
                .baseUrl(chatProperties.getUrl())
                .modelName(chatProperties.getModel())
                .temperature(chatProperties.getTemperature())
                .topP(chatProperties.getTopP())
                .maxTokens(chatProperties.getMaxTokens())
                .strictJsonSchema(true)
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .apiKey(chatProperties.getApiKey())
                .logRequests(chatProperties.getLogRequests())               // useful for debugging
                .logResponses(chatProperties.getLogResponses())
                .build();

        // Vision model from your vision properties
        this.visionProperties = visionProperties;
        this.visionModel = OpenAiChatModel.builder()
                .baseUrl(visionProperties.getUrl())
                .modelName(visionProperties.getModel())
                .temperature(visionProperties.getTemperature())
                .topP(visionProperties.getTopP())
                .maxTokens(visionProperties.getMaxTokens())
                .strictJsonSchema(true)
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .apiKey(visionProperties.getApiKey())
                .logRequests(visionProperties.getLogRequests())               // useful for debugging
                .logResponses(visionProperties.getLogResponses())
                .build();
    }

    // ==================== TEXT-ONLY (unchanged signature) ====================
    public String chat(@NotNull String systemPrompt, @NotNull String userMessage) {
        long startTime = System.currentTimeMillis();
        try {
            ChatResponse response = chatModel.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userMessage)
                    ))
                    .build());

            String result = getContent(response);
            LOG.info("chat result received successfully");
            return result;
        } catch (Exception e) {
            LOG.error("Text chat failed", e);
            throw new RuntimeException("Failed to call LLM", e);
        } finally {
            logDuration(startTime, "text chat");
        }
    }

    // ==================== VISION / MULTIMODAL ====================

    /**
     * Vision chat with image from public URL (http/https).
     * Works with GPT-4o, LLaVA, Qwen-VL, Claude-3, etc.
     */
    public String chatWithVision(@NotNull String systemPrompt,
                                 @NotNull String userMessage,
                                 @NotNull String imageUrl) {
        long startTime = System.currentTimeMillis();
        try {
            UserMessage userMsg = UserMessage.from(TextContent.from(userMessage),ImageContent.from(imageUrl));

            ChatResponse response = visionModel.chat(ChatRequest.builder()
                    .messages(List.of(SystemMessage.from(systemPrompt),userMsg)).build());

            String result = getContent(response);
            LOG.info("vision chat (URL) result received");
            return result;
        } catch (Exception e) {
            LOG.error("Vision chat failed for image URL: {}", imageUrl, e);
            throw new RuntimeException("Failed to call vision LLM", e);
        } finally {
            logDuration(startTime, "vision chat (URL)");
        }
    }

    /**
     * Vision chat with image already in memory (byte[]).
     * Ideal for MultipartFile uploads, screenshots, etc.
     */
    public String chatWithVision(@NotNull String systemPrompt,
                                 @NotNull String userMessage,
                                 byte[] imageBytes,
                                 String mimeType) {   // e.g. "image/jpeg", "image/png"
        long startTime = System.currentTimeMillis();
        try {
            Image image = Image.builder()
                    .base64Data(Base64.getEncoder().encodeToString(imageBytes))
                    .mimeType(mimeType)
                    .build();

            UserMessage userMsg = UserMessage.from(
                    TextContent.from(userMessage),
                    ImageContent.from(image)
            );

            ChatResponse response = visionModel.chat(ChatRequest.builder()
                    .messages(List.of(SystemMessage.from(systemPrompt),userMsg)).build());

            String result = getContent(response);
            LOG.info("vision chat (bytes) result received");
            return result;
        } catch (Exception e) {
            LOG.error("Vision chat failed for byte[] image", e);
            throw new RuntimeException("Failed to call vision LLM", e);
        } finally {
            logDuration(startTime, "vision chat (bytes)");
        }
    }

    public String analyzeImage(@NotNull Path imagePath, @NotNull String systemPrompt, @NotNull String userPrompt) throws IOException {
        LOG.info("Sending image: {}", imagePath.getFileName());
        String mimeType = PdfImageConverter.getMimeType(imagePath);
        return analyzeImage(Files.newInputStream(imagePath), mimeType, systemPrompt, userPrompt);
    }

    public String analyzeImage(@NotNull InputStream is, String mimeType,
                               @NotNull String systemPrompt, @NotNull String userPrompt) throws IOException {
        long startTime = System.currentTimeMillis();

        try {
            String base64 = PdfImageConverter.encodeImageToBase64(is);
            Image image = Image.builder().base64Data(base64).mimeType(mimeType).build();
            UserMessage userMsg = UserMessage.from(TextContent.from(userPrompt),ImageContent.from(image));
            ChatResponse response = visionModel.chat(ChatRequest.builder()
                    .messages(List.of(SystemMessage.from(systemPrompt),userMsg)).build());

            return getContent(response);
        } finally {
            logDuration(startTime, "analyzeImage");
        }
    }

    public List<String> analyzePdf(@NotNull Path pdfPath, @NotNull String systemPrompt, @NotNull String userPrompt) throws Exception {
        return analyzePdf(Files.newInputStream(pdfPath), systemPrompt, userPrompt);
    }

    public List<String> analyzePdf(@NotNull InputStream is, @NotNull String systemPrompt, @NotNull String userPrompt) throws Exception {
        List<String> responses = new ArrayList<>();

        List<PdfImageConverter.PageImage> pageImages =
                PdfImageConverter.convertPdfToGrayscaleImages(is, visionProperties.getDpi());

        for (PdfImageConverter.PageImage page : pageImages) {
            LOG.info("Sending Page {}/{}...", page.pageNumber(), pageImages.size());

            Image image = Image.builder().base64Data(page.base64()).mimeType(page.mimeType()).build();
            UserMessage userMsg = UserMessage.from(TextContent.from(userPrompt),ImageContent.from(image));
            ChatResponse response = visionModel.chat(ChatRequest.builder()
                    .messages(List.of(SystemMessage.from(systemPrompt), userMsg)).build());
            String result = getContent(response);
            responses.add("--- Page " + page.pageNumber() + " ---\n" + result);
        }
        return responses;
    }

    // ==================== HELPERS ====================
    private String getContent(ChatResponse response) {
        return response.aiMessage() != null && response.aiMessage().text() != null
                ? response.aiMessage().text()
                : "Empty response from LLM";
    }

    private void logDuration(long startTime, String operation) {
        long durationMs = System.currentTimeMillis() - startTime;
        Duration duration = Duration.ofMillis(durationMs);
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).getSeconds();
        LOG.info("{} completed in {}:{}", operation, minutes, seconds);
    }
}