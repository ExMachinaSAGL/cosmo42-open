package ch.exmachina.cosmo42.services.kb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FileConverterTest {

    private FileConverter fileConverter;
    private RestClient restClient;

    @BeforeEach
    void setUp() throws Exception {
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        fileConverter = new FileConverter(restClient);
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

}
