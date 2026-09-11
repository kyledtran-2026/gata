package local.kdt.gata.common.util;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ContentTypeUtils {

    private static final String DEFAULT = "application/octet-stream";

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("js", "text/javascript"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("zip", "application/zip"),
            Map.entry("gz", "application/gzip"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("avi", "video/x-msvideo")
    );

    private ContentTypeUtils() {}

    public static String fromFilename(String filename) {
        return fromExtension(extensionOf(filename)).orElse(DEFAULT);
    }

    public static Optional<String> fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(TYPES.get(extension.toLowerCase(Locale.ROOT)));
    }

    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1)
                ? name.substring(dot + 1)
                : "";
    }
}
