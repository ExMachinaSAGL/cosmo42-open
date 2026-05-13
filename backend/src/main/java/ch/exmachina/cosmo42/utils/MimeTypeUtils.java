package ch.exmachina.cosmo42.utils;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public class MimeTypeUtils {

    public static boolean isSupportedMimeType(MultipartFile file) {
        return SupportedMimeTypes.isSupported(getMimeType(file));
    }

    public static boolean isMimeType(MultipartFile file, SupportedMimeTypes mimeType) {
        return mimeType.getContentType().equals(getMimeType(file));
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
