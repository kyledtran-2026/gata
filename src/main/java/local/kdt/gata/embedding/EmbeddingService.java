package local.kdt.gata.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingService {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingService.class);
    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingProperties properties) {
        // Standardizing integration utilizing llama-swap's OpenAI emulation
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl(properties.getUrl())
                .apiKey(properties.getApiKey() != null ? properties.getApiKey() : "dummy-key")
                .modelName(properties.getModel())
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    public List<Embedding> embedAll(List<String> texts) {
        if (texts.isEmpty()) return List.of();

        LOG.info("Generating embeddings for batch of size={}", texts.size());
        List<TextSegment> segments = texts.stream()
                .map(t -> TextSegment.from(t.isEmpty() ? " " : t))
                .toList();

        return embeddingModel.embedAll(segments).content();
    }

    public Embedding embed(String text) {
        return embeddingModel.embed(text).content();
    }
}