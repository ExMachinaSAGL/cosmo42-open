package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.testsupport.FileFixtures;
import ch.exmachina.cosmo42.utils.SupportedMimeTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class MimeTypeServiceImplTest {

    MimeTypeService service;

    @BeforeEach
    void setUp() {
        service = new MimeTypeServiceImpl();
    }

    @Test
    void detectsRealPdfAsSupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", FileFixtures.singlePagePdf("hello"));

        assertThat(service.isSupportedMimeType(file)).isTrue();
        assertThat(service.isMimeType(file, SupportedMimeTypes.MIME_PDF)).isTrue();
        assertThat(service.isMimeType(file, SupportedMimeTypes.MIME_DOCX)).isFalse();
        assertThat(service.isMimeType(file, SupportedMimeTypes.MIME_XSLX)).isFalse();
    }

    @Test
    void detectsDocxAsSupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.docx", "application/octet-stream", FileFixtures.minimalDocx());

        assertThat(service.isSupportedMimeType(file)).isTrue();
        assertThat(service.isMimeType(file, SupportedMimeTypes.MIME_DOCX)).isTrue();
        assertThat(service.isMimeType(file, SupportedMimeTypes.MIME_PDF)).isFalse();
    }

    @Test
    void detectsXlsxAsSupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sheet.xlsx", "application/octet-stream", FileFixtures.minimalXlsx());

        assertThat(service.isSupportedMimeType(file)).isTrue();
        assertThat(service.isMimeType(file, SupportedMimeTypes.MIME_XSLX)).isTrue();
    }

    @Test
    void rejectsPlainTextAsUnsupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "just text".getBytes());

        assertThat(service.isSupportedMimeType(file)).isFalse();
    }

    @Test
    void rejectsEmptyFileAsUnsupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.bin", "application/octet-stream", new byte[0]);

        assertThat(service.isSupportedMimeType(file)).isFalse();
    }

    @Test
    void contentDrivesDetectionNotClientDeclaredContentType() {
        // Client SAYS PDF, but bytes are plain text — Tika sniffs the bytes.
        MockMultipartFile file = new MockMultipartFile(
                "file", "lying.pdf", "application/pdf", "not actually a pdf".getBytes());

        assertThat(service.isMimeType(file, SupportedMimeTypes.MIME_PDF)).isFalse();
    }

    @Test
    void supportedMimeTypesIsSupportedHandlesNullAndUnknown() {
        assertThat(SupportedMimeTypes.isSupported(null)).isFalse();
        assertThat(SupportedMimeTypes.isSupported("image/png")).isFalse();
        assertThat(SupportedMimeTypes.isSupported("application/pdf")).isTrue();
        assertThat(SupportedMimeTypes.isSupported(SupportedMimeTypes.MIME_DOCX.getContentType())).isTrue();
        assertThat(SupportedMimeTypes.isSupported(SupportedMimeTypes.MIME_XSLX.getContentType())).isTrue();
    }

    @Test
    void getMimeTypeFromBytesDetectsPdf() {
        byte[] bytes = FileFixtures.singlePagePdf("hi");

        assertThat(service.getMimeType(bytes, "doc.pdf"))
                .isEqualTo(SupportedMimeTypes.MIME_PDF.getContentType());
    }

    @Test
    void getMimeTypeFromBytesDetectsDocx() {
        byte[] bytes = FileFixtures.minimalDocx();

        assertThat(service.getMimeType(bytes, "doc.docx"))
                .isEqualTo(SupportedMimeTypes.MIME_DOCX.getContentType());
    }

    @Test
    void getMimeTypeFromBytesDetectsXlsx() {
        byte[] bytes = FileFixtures.minimalXlsx();

        assertThat(service.getMimeType(bytes, "sheet.xlsx"))
                .isEqualTo(SupportedMimeTypes.MIME_XSLX.getContentType());
    }

    @Test
    void getMimeTypeFromBytesDoesNotReturnSupportedForPlainText() {
        byte[] bytes = "plain text".getBytes();

        assertThat(SupportedMimeTypes.isSupported(service.getMimeType(bytes, "notes.txt"))).isFalse();
    }
}
