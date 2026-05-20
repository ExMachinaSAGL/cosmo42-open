package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.kb.KBDocumentChunker;
import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import ch.exmachina.cosmo42.testsupport.FileFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class KBDocumentServiceIntegrationTest extends AbstractIntegrationTest {

    @TempDir static Path storageRoot;

    @DynamicPropertySource
    static void overrideStoragePath(DynamicPropertyRegistry registry) {
        registry.add("cosmo42.fs.storage.path", storageRoot::toString);
    }

    @MockitoBean KBDocumentChunker chunker;
    @MockitoBean EmbeddingModel embeddingModel;

    @Autowired KBDocumentService service;
    @Autowired KBDocumentRepository documentRepo;
    @Autowired KBDocumentChunkRepository chunkRepo;

    MockMultipartFile file;

    @BeforeEach
    void cleanState() throws IOException {
        chunkRepo.deleteAll();
        documentRepo.deleteAll();
        if (Files.exists(storageRoot)) {
            try (var paths = Files.list(storageRoot)) {
                paths.forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
        file = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                FileFixtures.singlePagePdf("hello"));
    }

    @Test
    void happyPathPersistsDocumentChunksAndFile() throws Exception {
        when(chunker.extractRawChunks(any())).thenReturn(List.of(page("alpha", "beta")));
        stubEmbeddingResponseOfSize(2);

        service.saveKBDocument(file);

        assertThat(documentRepo.findAll()).hasSize(1);
        assertThat(chunkRepo.findAll()).hasSize(2);
        String uuid = documentRepo.findAll().getFirst().getUuid();
        assertThat(Files.exists(storageRoot.resolve(uuid))).isTrue();
    }

    @Test
    void embeddingFailureLeavesNoOrphanFileOrDbRow() throws Exception {
        when(chunker.extractRawChunks(any())).thenReturn(List.of(page("alpha")));
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenThrow(new RuntimeException("embedding service down"));

        assertThatThrownBy(() -> service.saveKBDocument(file))
                .isInstanceOf(RuntimeException.class);

        assertThat(documentRepo.findAll())
                .as("DB row must be rolled back when embedding fails")
                .isEmpty();
        assertThat(chunkRepo.findAll()).isEmpty();
        assertThat(filesInStorage())
                .as("File on disk must be removed when the transaction rolls back")
                .isEmpty();
    }

    @Test
    void chunkPersistenceFailureLeavesNoOrphanFileOrDbRow() throws Exception {
        when(chunker.extractRawChunks(any())).thenReturn(List.of(page("alpha")));
        stubEmbeddingResponseOfSize(1);
        // Force the chunk save step to fail by making the chunk reference a non-existent document.
        // Simpler: poison the embedding response to have fewer results than chunks → IndexOutOfBoundsException.
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(
                new EmbeddingResponse(List.of()));

        assertThatThrownBy(() -> service.saveKBDocument(file))
                .isInstanceOf(RuntimeException.class);

        assertThat(documentRepo.findAll()).isEmpty();
        assertThat(chunkRepo.findAll()).isEmpty();
        assertThat(filesInStorage()).isEmpty();
    }

    private List<Path> filesInStorage() throws IOException {
        if (!Files.exists(storageRoot)) {
            return List.of();
        }
        try (var paths = Files.list(storageRoot)) {
            return paths.toList();
        }
    }

    private void stubEmbeddingResponseOfSize(int n) {
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest req = invocation.getArgument(0);
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < req.getInstructions().size(); i++) {
                embeddings.add(new Embedding(new float[1024], i));
            }
            return new EmbeddingResponse(embeddings);
        });
    }

    private static DocumentPage page(String... contents) {
        DocumentPage p = new DocumentPage();
        List<Chunk> chunks = new ArrayList<>();
        for (String content : contents) {
            Chunk c = new Chunk();
            c.setType("text");
            c.setContent(content);
            c.setContinuesOnNextPage(false);
            chunks.add(c);
        }
        p.setChunks(chunks);
        return p;
    }
}
