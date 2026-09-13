package local.kdt.gata.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipBytesUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ZipBytesUtil.class);

    private static final int BUFFER_SIZE = 16 * 1024;
    /** Safety cap per entry. Raise if you expect large files. */
    private static final long MAX_ENTRY_BYTES = 200L * 1024 * 1024;
    /** Safety cap for total uncompressed size. */
    private static final long MAX_TOTAL_BYTES = 1L * 1024 * 1024 * 1024;

    private ZipBytesUtil() {}

    /**
     * Unzip {@code zipBytes} into filename/path → file bytes.
     * Directory entries, path-traversal names, and common junk (__MACOSX, .DS_Store) are skipped.
     */
    public static Map<String, byte[]> unzip(byte[] zipBytes) throws IOException {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("zipBytes is empty");
        }

        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;

        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String name = sanitizeEntryName(entry.getName());
                    if (name == null) {
                        LOG.debug("skipping zip entry: {}", entry.getName());
                        continue;
                    }

                    byte[] content = readEntry(zis, name, entry.getSize());
                    total += content.length;
                    if (total > MAX_TOTAL_BYTES) {
                        throw new IOException("unzip aborted: total uncompressed size exceeds limit");
                    }
                    files.put(name, content);
                } finally {
                    zis.closeEntry();
                }
            }
        }
        return files;
    }

    private static String sanitizeEntryName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String name = raw.replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }

        // zip-slip / traversal
        if (name.contains("..")) {
            return null;
        }
        // macOS / junk
        if (name.startsWith("__MACOSX/") || name.endsWith("/.DS_Store") || name.equals(".DS_Store")) {
            return null;
        }
        if (name.isBlank() || name.endsWith("/")) {
            return null;
        }
        return name;
    }

    private static byte[] readEntry(ZipInputStream zis, String name, long declaredSize) throws IOException {
        if (declaredSize > MAX_ENTRY_BYTES) {
            throw new IOException("zip entry too large: " + name);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(
                declaredSize > 0 && declaredSize <= Integer.MAX_VALUE
                        ? (int) declaredSize
                        : BUFFER_SIZE);
        byte[] buf = new byte[BUFFER_SIZE];
        long written = 0;
        int n;
        while ((n = zis.read(buf)) != -1) {
            written += n;
            if (written > MAX_ENTRY_BYTES) {
                throw new IOException("zip entry exceeded size limit: " + name);
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
