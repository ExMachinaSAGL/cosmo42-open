package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.entities.KBDocumentChunkType;
import ch.exmachina.cosmo42.exceptions.FileSaveException;
import ch.exmachina.cosmo42.exceptions.KBDocumentNotFoundException;
import ch.exmachina.cosmo42.mappers.KBDocumentMapper;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.fs.FileReference;
import ch.exmachina.cosmo42.services.fs.FileService;
import ch.exmachina.cosmo42.services.kb.KBDocumentChunker;
import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import ch.exmachina.cosmo42.testsupport.FakeClock;
import ch.exmachina.cosmo42.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KBDocumentServiceTest {

    KBDocumentRepository documentRepo;
    KBDocumentChunkRepository chunkRepo;
    FileService fileService;
    KBDocumentMapper mapper;
    KBDocumentChunker chunker;
    EmbeddingModel embeddingModel;
    OpenAiEmbeddingOptions embeddingOptions;
    Clock clock;
    KBDocumentService service;

    MockMultipartFile file;

    @BeforeEach
    void setUp() {
        documentRepo = mock(KBDocumentRepository.class);
        chunkRepo = mock(KBDocumentChunkRepository.class);
        fileService = mock(FileService.class);
        mapper = new KBDocumentMapper();
        chunker = mock(KBDocumentChunker.class);
        embeddingModel = mock(EmbeddingModel.class);
        embeddingOptions = OpenAiEmbeddingOptions.builder().model("test-embedding").build();
        clock = FakeClock.fixedAtFixedNow();
        service = new KBDocumentService(
                documentRepo, chunkRepo, fileService, mapper, chunker,
                embeddingModel, embeddingOptions, clock);
        file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "pdf-bytes".getBytes());
    }

    @Test
    void saveKBDocumentPersistsDocumentAndChunksWithEmbeddings() throws Exception {
        DocumentPage page = page(textChunk("first body"), tableChunk("| col |", "table summary"));
        when(chunker.extractRawChunks(file)).thenReturn(List.of(page));
        when(fileService.save(file)).thenReturn(FileReference.builder()
                .uuid("doc-uuid").fileName("doc.pdf").fileSize(123L).build());
        stubEmbeddingResponseOfSize(2);

        DocumentDTO dto = service.saveKBDocument(file);

        ArgumentCaptor<KBDocument> docCap = ArgumentCaptor.forClass(KBDocument.class);
        verify(documentRepo).save(docCap.capture());
        KBDocument saved = docCap.getValue();
        assertThat(saved.getUuid()).isEqualTo("doc-uuid");
        assertThat(saved.getFileName()).isEqualTo("doc.pdf");
        assertThat(saved.getFileSize()).isEqualTo(123L);
        assertThat(saved.getCreationTimestamp()).isEqualTo(Fixtures.FIXED_NOW);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KBDocumentChunk>> chunksCap = ArgumentCaptor.forClass(List.class);
        verify(chunkRepo).saveAll(chunksCap.capture());
        List<KBDocumentChunk> chunks = chunksCap.getValue();
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getType()).isEqualTo(KBDocumentChunkType.TEXT);
        assertThat(chunks.get(0).getContent()).isEqualTo("first body");
        assertThat(chunks.get(1).getType()).isEqualTo(KBDocumentChunkType.TABLE);
        assertThat(chunks.get(1).getSummary()).isEqualTo("table summary");
        for (KBDocumentChunk chunk : chunks) {
            assertThat(chunk.getEmbedding()).isNotNull().hasSize(1024);
        }

        assertThat(dto.getUuid()).isEqualTo("doc-uuid");
        assertThat(dto.getName()).isEqualTo("doc.pdf");
    }

    @Test
    void tableChunkEmbedsSummaryWhileTextChunkEmbedsContent() throws Exception {
        DocumentPage page = page(textChunk("plain text"), tableChunk("| table |", "describes the table"));
        when(chunker.extractRawChunks(file)).thenReturn(List.of(page));
        when(fileService.save(file)).thenReturn(FileReference.builder()
                .uuid("u").fileName("f").fileSize(1L).build());
        stubEmbeddingResponseOfSize(2);

        service.saveKBDocument(file);

        ArgumentCaptor<EmbeddingRequest> reqCap = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingModel).call(reqCap.capture());
        assertThat(reqCap.getValue().getInstructions())
                .containsExactly("plain text", "describes the table");
        assertThat(reqCap.getValue().getOptions()).isSameAs(embeddingOptions);
    }

    @Test
    void multiplePagesProduceFlattenedChunkList() throws Exception {
        DocumentPage page1 = page(textChunk("p1a"), textChunk("p1b"));
        DocumentPage page2 = page(textChunk("p2a"));
        when(chunker.extractRawChunks(file)).thenReturn(List.of(page1, page2));
        when(fileService.save(file)).thenReturn(FileReference.builder()
                .uuid("u").fileName("f").fileSize(1L).build());
        stubEmbeddingResponseOfSize(3);

        service.saveKBDocument(file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KBDocumentChunk>> chunksCap = ArgumentCaptor.forClass(List.class);
        verify(chunkRepo).saveAll(chunksCap.capture());
        assertThat(chunksCap.getValue())
                .extracting(KBDocumentChunk::getContent)
                .containsExactly("p1a", "p1b", "p2a");
    }

    @Test
    void chunkerIoExceptionTranslatesToFileSaveException() throws Exception {
        when(chunker.extractRawChunks(file)).thenThrow(new IOException("pdf parse failed"));

        assertThatThrownBy(() -> service.saveKBDocument(file))
                .isInstanceOf(FileSaveException.class);
        verifyNoInteractions(fileService);
        verifyNoInteractions(embeddingModel);
        verify(documentRepo, never()).save(any());
    }

    @Test
    void fileServiceIoExceptionTranslatesToFileSaveException() throws Exception {
        when(chunker.extractRawChunks(file)).thenReturn(List.of());
        when(fileService.save(file)).thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> service.saveKBDocument(file))
                .isInstanceOf(FileSaveException.class);
        verify(documentRepo, never()).save(any());
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void listAllKBDocumentsDelegatesToRepositoryAndMaps() {
        KBDocument d1 = Fixtures.document("u-1", "a.pdf");
        KBDocument d2 = Fixtures.document("u-2", "b.pdf");
        when(documentRepo.findAll()).thenReturn(List.of(d1, d2));

        List<DocumentDTO> result = service.listAllKBDocuments();

        assertThat(result).extracting(DocumentDTO::getUuid).containsExactly("u-1", "u-2");
        assertThat(result).extracting(DocumentDTO::getName).containsExactly("a.pdf", "b.pdf");
    }

    @Test
    void loadKBDocumentReturnsDtoWithFileContent() throws Exception {
        KBDocument doc = Fixtures.document("u-1", "a.pdf");
        when(documentRepo.findByUuid("u-1")).thenReturn(Optional.of(doc));
        when(fileService.load("u-1")).thenReturn(new byte[]{1, 2, 3});

        DocumentDTO dto = service.loadKBDocument("u-1");

        assertThat(dto.getUuid()).isEqualTo("u-1");
        assertThat(dto.getName()).isEqualTo("a.pdf");
        assertThat(dto.getContent()).containsExactly(1, 2, 3);
    }

    @Test
    void loadKBDocumentThrowsNotFoundWhenUuidMissing() {
        when(documentRepo.findByUuid("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadKBDocument("missing"))
                .isInstanceOf(KBDocumentNotFoundException.class);
    }

    @Test
    void loadKBDocumentWrapsFileServiceIoException() throws Exception {
        KBDocument doc = Fixtures.document("u-1", "a.pdf");
        when(documentRepo.findByUuid("u-1")).thenReturn(Optional.of(doc));
        when(fileService.load("u-1")).thenThrow(new IOException("read failed"));

        assertThatThrownBy(() -> service.loadKBDocument("u-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error downloading file");
    }

    @Test
    void deleteKBDocumentRemovesChunksDocumentAndFileInOrder() throws Exception {
        service.deleteKBDocument("u-1");

        var inOrder = inOrder(chunkRepo, documentRepo, fileService);
        inOrder.verify(chunkRepo).deleteByKbDocument_Uuid("u-1");
        inOrder.verify(documentRepo).deleteByUuid("u-1");
        inOrder.verify(fileService).delete("u-1");
    }

    @Test
    void deleteKBDocumentWrapsFileServiceIoException() throws Exception {
        org.mockito.Mockito.doThrow(new IOException("disk fail")).when(fileService).delete("u-1");

        assertThatThrownBy(() -> service.deleteKBDocument("u-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error deleting file");
    }

    private void stubEmbeddingResponseOfSize(int n) {
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest req = invocation.getArgument(0);
            List<Embedding> embeddings = new java.util.ArrayList<>();
            for (int i = 0; i < req.getInstructions().size(); i++) {
                embeddings.add(new Embedding(new float[1024], i));
            }
            return new EmbeddingResponse(embeddings);
        });
    }

    private static DocumentPage page(Chunk... chunks) {
        DocumentPage p = new DocumentPage();
        p.setChunks(new java.util.ArrayList<>(List.of(chunks)));
        return p;
    }

    private static Chunk textChunk(String content) {
        Chunk c = new Chunk();
        c.setType("text");
        c.setContent(content);
        c.setContinuesOnNextPage(false);
        return c;
    }

    private static Chunk tableChunk(String content, String summary) {
        Chunk c = new Chunk();
        c.setType("table");
        c.setContent(content);
        c.setSummary(summary);
        c.setContinuesOnNextPage(false);
        return c;
    }
}
