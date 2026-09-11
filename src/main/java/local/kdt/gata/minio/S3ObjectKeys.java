package local.kdt.gata.minio;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class S3ObjectKeys {

    /** AWS recommended safe set: 0-9 A-Z a-z ! - _ . * ' ( ) */
    private static final Pattern UNSAFE = Pattern.compile("[^0-9A-Za-z!\\-_.*'()]+");
    private static final Pattern REPEATED_SEPARATORS = Pattern.compile("[_\\-.]{2,}");
    private static final int MAX_KEY_BYTES = 1024;
    private static final String FALLBACK = "unnamed";

    private S3ObjectKeys() {}

    public static String toS3SafeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return FALLBACK;
        }

        String name = lastPathSegment(filename).trim();
        if (name.isEmpty()) {
            return FALLBACK;
        }

        String extension = "";
        int dot = name.lastIndexOf('.');
        // ".env" stays a hidden file; "photo.jpg" keeps the suffix
        if (dot > 0 && dot < name.length() - 1) {
            extension = name.substring(dot);
            name = name.substring(0, dot);
        }

        name = sanitizeSegment(name);
        extension = sanitizeSegment(extension);

        if (name.isEmpty() && (extension.isEmpty() || ".".equals(extension))) {
            return FALLBACK;
        }

        String combined = stripTrailingPeriods(name + extension);
        if (combined.isEmpty() || ".".equals(combined) || "..".equals(combined)) {
            return FALLBACK;
        }

        return truncateToMaxBytes(combined, extension);
    }

    private static String lastPathSegment(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return slash >= 0 ? filename.substring(slash + 1) : filename;
    }

    private static String sanitizeSegment(String value) {
        if (value.isEmpty()) {
            return "";
        }
        String sanitized = UNSAFE.matcher(value).replaceAll("_");
        return REPEATED_SEPARATORS.matcher(sanitized).replaceAll("_");
    }

    private static String stripTrailingPeriods(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '.') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String truncateToMaxBytes(String filename, String extension) {
        if (utf8Length(filename) <= MAX_KEY_BYTES) {
            return filename;
        }
        String ext = extension == null ? "" : stripTrailingPeriods(extension);
        int extBytes = utf8Length(ext);
        if (extBytes >= MAX_KEY_BYTES) {
            return cutToBytes(filename, MAX_KEY_BYTES);
        }
        String stem = filename.endsWith(ext)
                ? filename.substring(0, filename.length() - ext.length())
                : filename;
        return cutToBytes(stem, MAX_KEY_BYTES - extBytes) + ext;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String cutToBytes(String value, int maxBytes) {
        if (maxBytes <= 0) {
            return "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        int len = maxBytes;
        while (len > 0 && (bytes[len] & 0xC0) == 0x80) {
            len--;
        }
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }
}