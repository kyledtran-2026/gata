package local.kdt.gata.common.util;

public class FileUtil {
    public static String getJustFilename(String original) {
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException("Missing original filename");
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException("Invalid filename: " + original);
        }
        return name;
    }

    public static String getFileNameWithoutExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        // Strip directory path if present (handles both Unix and Windows separators)
        int lastSeparator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = (lastSeparator == -1) ? filename : filename.substring(lastSeparator + 1);

        // Strip extension
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) { // '>' instead of '>= 0' preserves hidden files like .gitignore
            return name.substring(0, lastDot);
        }

        return name;
    }
}
