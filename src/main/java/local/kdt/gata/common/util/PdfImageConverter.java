package local.kdt.gata.common.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class PdfImageConverter {

    public final static String MIME_TYPE = "image/png";

    public record PageImage(int pageNumber, String base64, String mimeType) {}

    /**
     * Static method you requested: PDF → list of grayscale PNG images (150 DPI by default)
     */
    public static List<PageImage> convertPdfToGrayscaleImages(Path pdfPath, int dpi) throws IOException {
        return convertPdfToGrayscaleImages(Files.newInputStream(pdfPath), dpi);
    }

    public static List<PageImage> convertPdfToGrayscaleImages(InputStream pdfInputStream, int dpi) throws IOException {
        List<PageImage> pages = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfInputStream)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();

            System.out.printf("Converting %d PDF pages to grayscale %d DPI images...%n", totalPages, dpi);

            for (int i = 0; i < totalPages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, dpi, ImageType.GRAY);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);

                String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                pages.add(new PageImage(i + 1, base64, MIME_TYPE));
            }
        }
        return pages;
    }


    public static String encodeImageToBase64(Path imagePath) throws IOException {
        return encodeImageToBase64(Files.newInputStream(imagePath));
    }

    public static String encodeImageToBase64(InputStream imgInputStream) throws IOException {
        byte[] bytes = imgInputStream.readAllBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String getMimeType(Path path) {
        try {
            String mime = java.nio.file.Files.probeContentType(path);
            if (mime != null && mime.startsWith("image/")) return mime;
        } catch (IOException ignored) {}
        // fallback
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg" : "image/png";
    }
}
