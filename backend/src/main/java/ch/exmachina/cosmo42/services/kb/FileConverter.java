package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.utils.MimeTypeUtils;
import ch.exmachina.cosmo42.utils.SupportedMimeTypes;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class FileConverter {

    RestClient libreofficeRestClient;

    private byte[] convertOfficeFileToPdf(byte[] fileBytes, String filename) {
        return libreofficeRestClient.post()
                .uri("/convert")
                .header("X-Filename", filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileBytes)
                .retrieve()
                .body(byte[].class)
                ;
    }

    public byte[] convertSupportedFileToPdfFromBytes(byte[] rawBytes, String originalFileName) throws IOException {
        String lower = originalFileName.toLowerCase();
        if (lower.endsWith(".docx") || lower.endsWith(".xlsx")) {
            log.info("Converting docx/xlsx to PDF from raw bytes");
            return convertOfficeFileToPdf(rawBytes, originalFileName);
        } else if (lower.endsWith(".pdf")) {
            return rawBytes;
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + originalFileName);
        }
    }

    public byte[] convertSupportedFileToPdf(MultipartFile file) throws IOException {
        byte[] pdfBytes;
        boolean isDocx = MimeTypeUtils.isMimeType(file, SupportedMimeTypes.MIME_DOCX);
        boolean isXslx = MimeTypeUtils.isMimeType(file, SupportedMimeTypes.MIME_XSLX);
        boolean isPdf = MimeTypeUtils.isMimeType(file, SupportedMimeTypes.MIME_PDF);

        if (isDocx || isXslx) {
            log.info("Converting docx/xlsx to PDF");
            pdfBytes = convertOfficeFileToPdf(file.getBytes(), file.getName());
        } else if (isPdf) {
            pdfBytes = file.getBytes();
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + file.getName());
        }
        return pdfBytes;
    }

    public List<byte[]> convertPdfToImages(byte[] pdfBytes) throws IOException {
        return convertPdfToImages(pdfBytes, 300f, 4096);
    }

    private List<byte[]> convertPdfToImages(byte[] pdfBytes,
                                            float dpi,
                                            int maxSidePx) throws IOException {
        List<byte[]> pages = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, dpi, ImageType.GRAY);
                image = resizeIfNeeded(image, maxSidePx);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "png", baos);
                    pages.add(baos.toByteArray());
                    log.debug("Page {}/{} → {} bytes (PNG Grayscale)", i + 1, document.getNumberOfPages(), baos.size());
                }
            }
        }

        log.info("PDF converted into {} PNG grayscale images at {}dpi", pages.size(), dpi);
        return pages;
    }

    private BufferedImage resizeIfNeeded(BufferedImage img, int maxSidePx) {
        int w = img.getWidth();
        int h = img.getHeight();

        if (w <= maxSidePx && h <= maxSidePx) {
            return img;
        }

        double scale = (double) maxSidePx / Math.max(w, h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);

        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(img, 0, 0, newW, newH, null);
        g.dispose();

        return resized;
    }
}