package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.services.MimeTypeService;
import ch.exmachina.cosmo42.testsupport.FileFixtures;
import ch.exmachina.cosmo42.utils.SupportedMimeTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FileConverterTest {

    private static final byte[] PDF_FROM_LIBREOFFICE = "fake-libreoffice-pdf".getBytes();

    MockRestServiceServer mockServer;
    FileConverter converter;
    MimeTypeService mimeTypeService;
    private FileConverter fileConverter;
    private RestClient restClient;

    @BeforeEach
    void setUp() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://libreoffice-test");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        mimeTypeService = mock(MimeTypeService.class);
        converter = new FileConverter(builder.build(), mimeTypeService);
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        fileConverter = new FileConverter(restClient);
    }

    @Test
    void docxRoutesThroughLibreofficeAndReturnsConvertedPdf() throws Exception {
        byte[] docxBytes = FileFixtures.minimalDocx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.docx", "application/octet-stream", docxBytes);
        mockServer.expect(requestTo("http://libreoffice-test/convert"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(content().bytes(docxBytes))
                .andRespond(withSuccess(PDF_FROM_LIBREOFFICE, MediaType.APPLICATION_OCTET_STREAM));

        stubFileType(file, SupportedMimeTypes.MIME_DOCX);

        byte[] result = converter.convertSupportedFileToPdf(file);

        assertThat(result).containsExactly(PDF_FROM_LIBREOFFICE);
        mockServer.verify();
    }

    @Test
    void xlsxRoutesThroughLibreoffice() throws Exception {
        byte[] xlsxBytes = FileFixtures.minimalXlsx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "sheet.xlsx", "application/octet-stream", xlsxBytes);
        mockServer.expect(requestTo("http://libreoffice-test/convert"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(PDF_FROM_LIBREOFFICE, MediaType.APPLICATION_OCTET_STREAM));

        stubFileType(file, SupportedMimeTypes.MIME_XSLX);

        byte[] result = converter.convertSupportedFileToPdf(file);

        assertThat(result).containsExactly(PDF_FROM_LIBREOFFICE);
        mockServer.verify();
    }

    @Test
    void pdfPassthroughDoesNotCallLibreoffice() throws Exception {
        byte[] pdfBytes = FileFixtures.singlePagePdf("hello");
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", pdfBytes);
        // No expectations registered on mockServer — any call would fail.

        stubFileType(file, SupportedMimeTypes.MIME_PDF);

        byte[] result = converter.convertSupportedFileToPdf(file);

        assertThat(result).containsExactly(pdfBytes);
        mockServer.verify();
    }

    @Test
    void unsupportedFileTypeThrowsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "just text".getBytes());
        when(mimeTypeService.isMimeType(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> converter.convertSupportedFileToPdf(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void libreofficeServerErrorPropagates() {
        byte[] docxBytes = FileFixtures.minimalDocx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.docx", "application/octet-stream", docxBytes);
        mockServer.expect(requestTo("http://libreoffice-test/convert"))
                .andRespond(withServerError());

        stubFileType(file, SupportedMimeTypes.MIME_DOCX);

        assertThatThrownBy(() -> converter.convertSupportedFileToPdf(file))
                .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
    }

    @Test
    void libreofficeClientErrorPropagates() {
        byte[] docxBytes = FileFixtures.minimalDocx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.docx", "application/octet-stream", docxBytes);
        mockServer.expect(requestTo("http://libreoffice-test/convert"))
                .andRespond(withBadRequest());

        stubFileType(file, SupportedMimeTypes.MIME_DOCX);

        assertThatThrownBy(() -> converter.convertSupportedFileToPdf(file))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
    }

    @Test
    void convertSupportedFileToPdfFromBytes_pdfBytes_returnsSameBytes() throws IOException {
        byte[] pdf = pdfHeader();

        byte[] result = fileConverter.convertSupportedFileToPdfFromBytes(pdf, "doc.pdf");

        assertThat(result).isSameAs(pdf);
        verifyNoInteractions(restClient);
    }

    @Test
    void convertSupportedFileToPdfFromBytes_unsupported_throws() {
        byte[] txt = "plain text content".getBytes();

        assertThatThrownBy(() -> fileConverter.convertSupportedFileToPdfFromBytes(txt, "note.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    private byte[] pdfHeader() {
        // Minimal valid PDF magic + EOF marker so Tika detects application/pdf.
        return ("%PDF-1.4\n%âãÏÓ\n1 0 obj<<>>endobj\nxref\n0 1\n0000000000 65535 f \n"
                + "trailer<<>>\nstartxref\n0\n%%EOF").getBytes();
    }

    private void stubFileType(MockMultipartFile file, SupportedMimeTypes type) {
        for (SupportedMimeTypes t : SupportedMimeTypes.values()) {
            when(mimeTypeService.isMimeType(file, t)).thenReturn(t == type);
        }
    }
}
