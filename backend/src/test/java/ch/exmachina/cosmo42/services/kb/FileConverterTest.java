package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.services.MimeTypeService;
import ch.exmachina.cosmo42.testsupport.FileFixtures;
import ch.exmachina.cosmo42.utils.SupportedMimeTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FileConverterTest {

    private static final byte[] PDF_FROM_LIBREOFFICE = "fake-libreoffice-pdf".getBytes();

    MockRestServiceServer mockServer;
    FileConverter converter;
    MimeTypeService mimeTypeService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://libreoffice-test");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        mimeTypeService = mock(MimeTypeService.class);
        converter = new FileConverter(builder.build(), mimeTypeService);
    }

    @Test
    void docxRoutesThroughLibreofficeAndReturnsConvertedPdf() {
        byte[] docxBytes = FileFixtures.minimalDocx();
        when(mimeTypeService.getMimeType(docxBytes, "doc.docx"))
                .thenReturn(SupportedMimeTypes.MIME_DOCX.getContentType());
        mockServer.expect(requestTo("http://libreoffice-test/convert"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(content().bytes(docxBytes))
                .andRespond(withSuccess(PDF_FROM_LIBREOFFICE, MediaType.APPLICATION_OCTET_STREAM));

        byte[] result = converter.convertSupportedFileToPdfFromBytes(docxBytes, "doc.docx");

        assertThat(result).containsExactly(PDF_FROM_LIBREOFFICE);
        mockServer.verify();
    }

    @Test
    void xlsxRoutesThroughLibreoffice() {
        byte[] xlsxBytes = FileFixtures.minimalXlsx();
        when(mimeTypeService.getMimeType(xlsxBytes, "sheet.xlsx"))
                .thenReturn(SupportedMimeTypes.MIME_XSLX.getContentType());
        mockServer.expect(requestTo("http://libreoffice-test/convert"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(PDF_FROM_LIBREOFFICE, MediaType.APPLICATION_OCTET_STREAM));

        byte[] result = converter.convertSupportedFileToPdfFromBytes(xlsxBytes, "sheet.xlsx");

        assertThat(result).containsExactly(PDF_FROM_LIBREOFFICE);
        mockServer.verify();
    }

    @Test
    void pdfPassthroughDoesNotCallLibreoffice() {
        byte[] pdfBytes = FileFixtures.singlePagePdf("hello");
        when(mimeTypeService.getMimeType(pdfBytes, "x.pdf"))
                .thenReturn(SupportedMimeTypes.MIME_PDF.getContentType());

        byte[] result = converter.convertSupportedFileToPdfFromBytes(pdfBytes, "x.pdf");

        assertThat(result).isSameAs(pdfBytes);
        mockServer.verify();
    }

    @Test
    void unsupportedFileTypeThrowsIllegalArgument() {
        byte[] txt = "just text".getBytes();
        when(mimeTypeService.getMimeType(txt, "notes.txt")).thenReturn("text/plain");

        assertThatThrownBy(() -> converter.convertSupportedFileToPdfFromBytes(txt, "notes.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void libreofficeServerErrorPropagates() {
        byte[] docxBytes = FileFixtures.minimalDocx();
        when(mimeTypeService.getMimeType(docxBytes, "doc.docx"))
                .thenReturn(SupportedMimeTypes.MIME_DOCX.getContentType());
        mockServer.expect(requestTo("http://libreoffice-test/convert"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> converter.convertSupportedFileToPdfFromBytes(docxBytes, "doc.docx"))
                .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
    }

    @Test
    void libreofficeClientErrorPropagates() {
        byte[] docxBytes = FileFixtures.minimalDocx();
        when(mimeTypeService.getMimeType(docxBytes, "doc.docx"))
                .thenReturn(SupportedMimeTypes.MIME_DOCX.getContentType());
        mockServer.expect(requestTo("http://libreoffice-test/convert"))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> converter.convertSupportedFileToPdfFromBytes(docxBytes, "doc.docx"))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
    }


}
