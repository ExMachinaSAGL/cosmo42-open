package ch.exmachina.cosmo42.utils;

import ch.exmachina.cosmo42.testsupport.FileFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class MimeTypeUtilsTest {

    @Test
    void detectsRealPdfAsSupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", FileFixtures.singlePagePdf("hello"));

        assertThat(MimeTypeUtils.isSupportedMimeType(file)).isTrue();
        assertThat(MimeTypeUtils.isMimeType(file, SupportedMimeTypes.MIME_PDF)).isTrue();
        assertThat(MimeTypeUtils.isMimeType(file, SupportedMimeTypes.MIME_DOCX)).isFalse();
        assertThat(MimeTypeUtils.isMimeType(file, SupportedMimeTypes.MIME_XSLX)).isFalse();
    }

    @Test
    void detectsDocxAsSupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.docx", "application/octet-stream", FileFixtures.minimalDocx());

        assertThat(MimeTypeUtils.isSupportedMimeType(file)).isTrue();
        assertThat(MimeTypeUtils.isMimeType(file, SupportedMimeTypes.MIME_DOCX)).isTrue();
        assertThat(MimeTypeUtils.isMimeType(file, SupportedMimeTypes.MIME_PDF)).isFalse();
    }

    @Test
    void detectsXlsxAsSupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sheet.xlsx", "application/octet-stream", FileFixtures.minimalXlsx());

        assertThat(MimeTypeUtils.isSupportedMimeType(file)).isTrue();
        assertThat(MimeTypeUtils.isMimeType(file, SupportedMimeTypes.MIME_XSLX)).isTrue();
    }

    @Test
    void rejectsPlainTextAsUnsupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "just text".getBytes());

        assertThat(MimeTypeUtils.isSupportedMimeType(file)).isFalse();
    }

    @Test
    void rejectsEmptyFileAsUnsupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.bin", "application/octet-stream", new byte[0]);

        assertThat(MimeTypeUtils.isSupportedMimeType(file)).isFalse();
    }

    @Test
    void contentDrivesDetectionNotClientDeclaredContentType() {
        // Client SAYS PDF, but bytes are plain text — Tika sniffs the bytes.
        MockMultipartFile file = new MockMultipartFile(
                "file", "lying.pdf", "application/pdf", "not actually a pdf".getBytes());

        assertThat(MimeTypeUtils.isMimeType(file, SupportedMimeTypes.MIME_PDF)).isFalse();
    }

    @Test
    void supportedMimeTypesIsSupportedHandlesNullAndUnknown() {
        assertThat(SupportedMimeTypes.isSupported(null)).isFalse();
        assertThat(SupportedMimeTypes.isSupported("image/png")).isFalse();
        assertThat(SupportedMimeTypes.isSupported("application/pdf")).isTrue();
        assertThat(SupportedMimeTypes.isSupported(SupportedMimeTypes.MIME_DOCX.getContentType())).isTrue();
        assertThat(SupportedMimeTypes.isSupported(SupportedMimeTypes.MIME_XSLX.getContentType())).isTrue();
    }
}
