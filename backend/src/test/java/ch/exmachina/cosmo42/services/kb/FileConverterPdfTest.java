package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.services.MimeTypeService;
import ch.exmachina.cosmo42.testsupport.FileFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FileConverterPdfTest {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final int MAX_SIDE_PX = 4096;

    FileConverter converter;

    @BeforeEach
    void setUp() {
        // No HTTP calls in this test; constructor still requires a RestClient.
        converter = new FileConverter(RestClient.builder().build(), mock(MimeTypeService.class));
    }

    @Test
    void singlePagePdfRendersToOnePng() throws IOException {
        List<byte[]> pages = converter.convertPdfToImages(FileFixtures.singlePagePdf("hello"));

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0)).startsWith(PNG_MAGIC);
    }

    @Test
    void multiPagePdfPreservesPageCount() throws IOException {
        List<byte[]> pages = converter.convertPdfToImages(FileFixtures.multiPagePdf(3));

        assertThat(pages).hasSize(3);
        for (byte[] page : pages) {
            assertThat(page).startsWith(PNG_MAGIC);
        }
    }

    @Test
    void oversizedPageGetsResizedBelowMaxSide() throws IOException {
        // 2000pt × 2000pt at 300 DPI renders to ~8333 px before resize.
        byte[] pdf = FileFixtures.oversizedPdf(2000, 2000);

        List<byte[]> pages = converter.convertPdfToImages(pdf);

        assertThat(pages).hasSize(1);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(pages.get(0)));
        assertThat(img.getWidth()).isLessThanOrEqualTo(MAX_SIDE_PX);
        assertThat(img.getHeight()).isLessThanOrEqualTo(MAX_SIDE_PX);
        assertThat(Math.max(img.getWidth(), img.getHeight())).isEqualTo(MAX_SIDE_PX);
    }

    @Test
    void normalSizedPageNotResized() throws IOException {
        // A4 at 300 DPI = 595×842pt → ~2480×3508 px, well under the 4096 ceiling.
        byte[] pdf = FileFixtures.singlePagePdf("hello");

        List<byte[]> pages = converter.convertPdfToImages(pdf);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(pages.get(0)));
        assertThat(img.getWidth()).isLessThan(MAX_SIDE_PX);
        assertThat(img.getHeight()).isLessThan(MAX_SIDE_PX);
    }

    @Test
    void invalidPdfBytesThrowIoException() {
        byte[] notAPdf = "not a pdf at all".getBytes();

        assertThatThrownBy(() -> converter.convertPdfToImages(notAPdf))
                .isInstanceOf(IOException.class);
    }
}
