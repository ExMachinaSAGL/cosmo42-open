package ch.exmachina.cosmo42.controllers;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public class ControllerUtils {

    final static String MIME_PDF = "application/pdf";
    final static String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    final static String MIME_XSLX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static boolean isSupportedMimeType(MultipartFile file) {
        String mimeType = getMimeType(file);
        return mimeType.equals(MIME_PDF) || mimeType.equals(MIME_DOCX) || mimeType.equals(MIME_XSLX);
    }

    private static String getMimeType(MultipartFile file) {
        AutoDetectParser parser = new AutoDetectParser();
        Detector detector = parser.getDetector();
        try {
            Metadata metadata = new Metadata();
            metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, file.getName());
            TikaInputStream stream = TikaInputStream.get(file.getInputStream());
            MediaType mediaType = detector.detect(stream, metadata);
            return mediaType.toString();
        } catch (IOException e) {
            return MimeTypes.OCTET_STREAM;
        }
    }
}
