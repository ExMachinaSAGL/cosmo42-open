package ch.exmachina.cosmo42.utils;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public class MimeTypeUtils {

    public static boolean isSupportedMimeType(MultipartFile file) {
        return SupportedMimeTypes.isSupported(getMimeType(file));
    }

    public static String getMimeType(byte[] bytes, String fileName) {
        return detect(TikaInputStream.get(bytes), fileName);
    }

    private static String getMimeType(MultipartFile file) {
        try {
            return detect(TikaInputStream.get(file.getInputStream()), file.getName());
        } catch (IOException e) {
            return MimeTypes.OCTET_STREAM;
        }
    }

    private static String detect(TikaInputStream stream, String fileName) {
        try {
            Detector detector = new AutoDetectParser().getDetector();
            Metadata metadata = new Metadata();
            metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            return detector.detect(stream, metadata).toString();
        } catch (IOException e) {
            return MimeTypes.OCTET_STREAM;
        }
    }
}
