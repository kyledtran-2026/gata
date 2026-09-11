package local.kdt.gata.common.llm;

import java.util.Map;
import java.util.HashMap;

public class PromptUtils {

    private static final String USER_PROMPT_TEMPLATE = """
        Document context:
        - Content type: {{type}}
        - Page number: {{page_idx}}
        - Section / Heading: {{section_title}}

        Content to summarize:
        {{content}}

        Task: Write a concise, information-dense summary of the content above. The summary will be used to create a vector embedding for semantic retrieval.
        """;

    /**
     * Renders the user prompt by replacing {{placeholders}} with actual values.
     */
    public static String renderUserPrompt(Map<String, Object> variables) {
        String prompt = USER_PROMPT_TEMPLATE;

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = (entry.getValue() != null) ? entry.getValue().toString() : "";
            prompt = prompt.replace(placeholder, value);
        }

        return prompt.trim();
    }

    /**
     * Convenience method when you have a MarkdownSection + MinerU metadata.
     */
    public static String renderUserPromptForSection(
            String type,
            Integer pageIdx,
            String sectionTitle,
            String content) {

        Map<String, Object> vars = new HashMap<>();
        vars.put("type", type != null ? type : "text");
        vars.put("page_idx", pageIdx != null ? pageIdx : "unknown");
        vars.put("section_title", sectionTitle != null ? sectionTitle : "");
        vars.put("content", content != null ? content : "");

        return renderUserPrompt(vars);
    }
}