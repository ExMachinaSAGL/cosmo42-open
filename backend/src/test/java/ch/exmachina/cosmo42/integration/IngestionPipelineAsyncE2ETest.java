package ch.exmachina.cosmo42.integration;

import ch.exmachina.cosmo42.BaseIT;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.repositories.IngestionJobPageRepository;
import ch.exmachina.cosmo42.repositories.IngestionJobRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.kb.FileConverter;
import ch.exmachina.cosmo42.services.kb.KBDocumentChunker;
import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import ch.exmachina.cosmo42.testsupport.EmbeddingMocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.BodyInserters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class IngestionPipelineAsyncE2ETest extends BaseIT {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void overrideStoragePath(DynamicPropertyRegistry registry) {
        registry.add("cosmo42.fs.storage.path", storageRoot::toString);
    }

    @MockitoBean
    FileConverter fileConverter;
    @MockitoBean
    KBDocumentChunker kbDocumentChunker;
    @MockitoBean
    EmbeddingModel embeddingModel;

    @Autowired
    IngestionJobRepository ingestionJobRepository;
    @Autowired
    IngestionJobPageRepository ingestionJobPageRepository;
    @Autowired
    KBDocumentRepository kbDocumentRepository;
    @Autowired
    KBDocumentChunkRepository kbDocumentChunkRepository;

    @AfterEach
    void cleanUp() throws Exception {
        kbDocumentChunkRepository.deleteAll();
        kbDocumentRepository.deleteAll();
        ingestionJobPageRepository.deleteAll();
        ingestionJobRepository.deleteAll();
        if (Files.exists(storageRoot)) {
            try (var paths = Files.list(storageRoot)) {
                paths.forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test
    void uploadKicksOffAsyncIngestionAndDriveJobToCompleted() throws Exception {
        byte[] pdfBytes = "%PDF-1.4\nfake".getBytes();
        when(fileConverter.convertSupportedFileToPdfFromBytes(any(), any())).thenReturn(pdfBytes);
        when(fileConverter.convertPdfToImages(any())).thenReturn(List.of(new byte[]{1}, new byte[]{2}));
        stubChunkerToReturnTwoPagesOfText();
        EmbeddingMocks.stubReturningZeros(embeddingModel, 1024);

        String fileUuid = postPdfAndExpectAccepted("doc.pdf", pdfBytes);

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    Optional<IngestionJob> job = ingestionJobRepository.findByStoredFileUuid(fileUuid);
                    assertThat(job).isPresent();
                    assertThat(job.get().getStatus()).isEqualTo(IngestionJobStatus.COMPLETED);
                });

        IngestionJob completed = ingestionJobRepository.findByStoredFileUuid(fileUuid).orElseThrow();
        assertThat(completed.getKbDocumentUuid()).isNotNull();
        var kbDocument = kbDocumentRepository.findByUuid(completed.getKbDocumentUuid()).orElseThrow();
        assertThat(kbDocumentChunkRepository.countByKbDocument(kbDocument)).isEqualTo(2);
    }

    @Test
    void uploadFailingChunkerEventuallyMarksJobFailed() throws Exception {
        byte[] pdfBytes = "%PDF-1.4\nfake".getBytes();
        when(fileConverter.convertSupportedFileToPdfFromBytes(any(), any())).thenReturn(pdfBytes);
        when(fileConverter.convertPdfToImages(any())).thenReturn(List.of(new byte[]{1}));
        doAnswer(inv -> {
            throw new RuntimeException("chunker boom");
        })
                .when(kbDocumentChunker).processPages(any(), any(), any());

        String fileUuid = postPdfAndExpectAccepted("broken.pdf", pdfBytes);

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    IngestionJob job = ingestionJobRepository.findByStoredFileUuid(fileUuid).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
                    assertThat(job.getErrorMessage()).isNotBlank();
                });
        assertThat(kbDocumentChunkRepository.findAll()).isEmpty();
    }

    private String postPdfAndExpectAccepted(String filename, byte[] bytes) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.APPLICATION_PDF);

        var dto = webTestClient.post()
                .uri("/api/v1/kb/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.ACCEPTED)
                .expectBody(ch.exmachina.cosmo42.dto.DocumentDTO.class)
                .returnResult().getResponseBody();

        assertThat(dto).isNotNull();
        assertThat(dto.getFileUuid()).isNotBlank();
        return dto.getFileUuid();
    }

    @SuppressWarnings("unchecked")
    private void stubChunkerToReturnTwoPagesOfText() {
        doAnswer(invocation -> {
            BiConsumer<Integer, DocumentPage> onPageComplete = invocation.getArgument(2);
            onPageComplete.accept(0, pageOf(textChunk("page 1 body")));
            onPageComplete.accept(1, pageOf(textChunk("page 2 body")));
            return null;
        }).when(kbDocumentChunker).processPages(any(), any(), any());
        when(kbDocumentChunker.mergePages(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static DocumentPage pageOf(Chunk... chunks) {
        DocumentPage p = new DocumentPage();
        p.setChunks(new ArrayList<>(List.of(chunks)));
        return p;
    }

    private static Chunk textChunk(String content) {
        Chunk c = new Chunk();
        c.setType("text");
        c.setContent(content);
        c.setContinuesOnNextPage(false);
        return c;
    }
}
