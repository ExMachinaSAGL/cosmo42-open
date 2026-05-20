package ch.exmachina.cosmo42.utils;

import java.util.Arrays;

public enum SupportedMimeTypes {
    MIME_PDF ("application/pdf"),
    MIME_DOCX ("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    MIME_XSLX ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String contentType;

    SupportedMimeTypes(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean matches(String mimeType) {
        return contentType.equals(mimeType);
    }

    public static boolean isSupported(String contentType) {
        if (contentType == null) {
            return false;
        }
        return Arrays.stream(SupportedMimeTypes.values()).anyMatch(mimeType -> mimeType.contentType.equals(contentType));
    }
}
