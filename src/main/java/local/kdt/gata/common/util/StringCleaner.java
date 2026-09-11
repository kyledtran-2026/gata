package local.kdt.gata.common.util;

public class StringCleaner {
    /**
     * Trims all strings
     * Remove empty lines if there are more than 1.
     * @param input
     * @return
     */
    public static String cleanStringContent(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Normalize all line endings to \n and trim the whole string
        String normalized = input
                .replaceAll("\\r\\n?", "\n")  // Convert \r\n and \r to \n
                .trim();

        if (normalized.isEmpty()) {
            return "";
        }

        // Split into lines
        String[] lines = normalized.split("\n");

        StringBuilder result = new StringBuilder();
        boolean lastWasEmpty = false;

        for (String line : lines) {
            String trimmedLine = line.trim();  // Remove leading/trailing whitespace on this line

            if (trimmedLine.isEmpty()) {
                // This is an empty (or whitespace-only) line
                if (!lastWasEmpty) {
                    result.append("\n");  // Add exactly one empty line
                    lastWasEmpty = true;
                }
                // else: skip additional consecutive empty lines
            } else {
                // Non-empty line
                if (result.length() > 0) {
                    result.append("\n");
                }
                result.append(trimmedLine);
                lastWasEmpty = false;
            }
        }

        return result.toString();
    }

    /**
     * Basic cleanup to remove excessive whitespace or null characters
     * that can confuse the LLM consolidator.
     */
    public static String cleanText(String text) {
        if (text == null) return "";
        return text.trim()
                .replaceAll("\u0000", "") // Remove null bytes
                .replaceAll("(?m)^[ \t]*\r?\n", ""); // Remove empty lines
    }
}
