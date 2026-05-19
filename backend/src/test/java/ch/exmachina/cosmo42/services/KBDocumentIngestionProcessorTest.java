package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.BaseTest;
import ch.exmachina.cosmo42.config.IngestionProperties;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.fs.FileService;
import ch.exmachina.cosmo42.services.kb.FileConverter;
import ch.exmachina.cosmo42.services.kb.KBDocumentChunker;
import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KBDocumentIngestionProcessorTest extends BaseTest {

    @Mock IngestionJobService ingestionJobService;
    @Mock KBDocumentChunker kbDocumentChunker;
    @Mock FileConverter fileConverter;
    @Mock FileService fileService;
    @Mock KBDocumentRepository kbDocumentRepository;
    @Mock KBDocumentChunkRepository kbDocumentChunkRepository;
    @Mock EmbeddingModel embeddingModel;
    @Mock OpenAiEmbeddingOptions embeddingModelOptions;

    private KBDocumentIngestionProcessor processor;

    private static final int MAX_ATTEMPTS = 3;

    @BeforeEach
    void setUp() {
        IngestionProperties ingestionProperties = new IngestionProperties();
        ingestionProperties.setMaxPageAttempts(MAX_ATTEMPTS);
        processor = new KBDocumentIngestionProcessor(
                ingestionJobService, kbDocumentChunker, fileConverter, fileService,
                kbDocumentRepository, kbDocumentChunkRepository,
                embeddingModel, embeddingModelOptions, ingestionProperties);
    }

    @Test
    void processInternal_happyPath_marksCompletedAndEmbedsChunks() throws IOException {
        IngestionJob job = newJob("j1", "doc.pdf");
        when(ingestionJobService.findByUuid("j1")).thenReturn(Optional.of(job));

        when(fileService.load("stored-uuid")).thenReturn(new byte[]{1});
        when(fileConverter.convertSupportedFileToPdfFromBytes(any(), eq("doc.pdf"))).thenReturn(new byte[]{2});
        when(fileConverter.convertPdfToImages(any())).thenReturn(List.of(new byte[]{}, new byte[]{}));

        doAnswer(inv -> { job.setTotalPages(inv.getArgument(1)); return null; })
                .when(ingestionJobService).setTotalPages(eq("j1"), anyInt());
        doAnswer(inv -> { job.setKbDocumentUuid(inv.getArgument(1)); return null; })
                .when(ingestionJobService).setKbDocumentUuid(eq("j1"), anyString());

        when(ingestionJobService.findRetryablePageIndices(any(), eq(MAX_ATTEMPTS)))
                .thenReturn(new LinkedHashSet<>(List.of(0, 1)))
                .thenReturn(new LinkedHashSet<>());

        doAnswer(inv -> {
            BiConsumer<Integer, DocumentPage> cb = inv.getArgument(2);
            cb.accept(0, new DocumentPage(List.of(new Chunk("text", "a", null, false))));
            cb.accept(1, new DocumentPage(List.of(new Chunk("text", "b", null, false))));
            return null;
        }).when(kbDocumentChunker).processPages(any(), any(), any());
        when(ingestionJobService.countExhaustedFailures(any(), eq(MAX_ATTEMPTS))).thenReturn(0L);
        when(ingestionJobService.loadCompletedPages(any())).thenReturn(List.of(
                new DocumentPage(List.of(new Chunk("text", "a", null, false))),
                new DocumentPage(List.of(new Chunk("text", "b", null, false)))));
        when(kbDocumentChunker.mergePages(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kbDocumentRepository.findByUuid(anyString())).thenReturn(Optional.of(kbDoc("stored-uuid")));
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(embedResponse(2));

        processor.processInternal("j1");

        verify(ingestionJobService).markProcessing("j1");
        verify(ingestionJobService).setTotalPages("j1", 2);
        verify(kbDocumentRepository).save(any(KBDocument.class));
        verify(kbDocumentChunkRepository).deleteByKbDocument_Uuid("stored-uuid");

        ArgumentCaptor<List<KBDocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(kbDocumentChunkRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);

        verify(ingestionJobService).markChunksEmbedded("j1");
        verify(ingestionJobService).markCompleted("j1");
        verify(ingestionJobService, never()).markFailed(any(), any());
        verify(ingestionJobService, never()).markInterrupted(any());
    }

    @Test
    void processInternal_exhaustedPages_marksFailed() throws IOException {
        IngestionJob job = newJob("j2", "doc.pdf");
        when(ingestionJobService.findByUuid("j2")).thenReturn(Optional.of(job));
        when(fileService.load(any())).thenReturn(new byte[]{1});
        when(fileConverter.convertSupportedFileToPdfFromBytes(any(), any())).thenReturn(new byte[]{2});
        when(fileConverter.convertPdfToImages(any())).thenReturn(List.of(new byte[]{}));
        doAnswer(inv -> { job.setTotalPages(inv.getArgument(1)); return null; })
                .when(ingestionJobService).setTotalPages(eq("j2"), anyInt());

        when(ingestionJobService.findRetryablePageIndices(any(), eq(MAX_ATTEMPTS)))
                .thenReturn(new LinkedHashSet<>(List.of(0)));
        doAnswer(inv -> { ((BiConsumer<Integer, DocumentPage>) inv.getArgument(2)).accept(0, null); return null; })
                .when(kbDocumentChunker).processPages(any(), any(), any());
        when(ingestionJobService.countExhaustedFailures(any(), eq(MAX_ATTEMPTS))).thenReturn(1L);

        processor.processInternal("j2");

        verify(ingestionJobService).markFailed(eq("j2"), contains("max attempts"));
        verify(ingestionJobService, never()).markCompleted(any());
        verify(kbDocumentRepository, never()).save(any(KBDocument.class));
    }

    @Test
    void processInternal_retryablePagesRemain_marksInterrupted() throws IOException {
        IngestionJob job = newJob("j3", "doc.pdf");
        when(ingestionJobService.findByUuid("j3")).thenReturn(Optional.of(job));
        when(fileService.load(any())).thenReturn(new byte[]{1});
        when(fileConverter.convertSupportedFileToPdfFromBytes(any(), any())).thenReturn(new byte[]{2});
        when(fileConverter.convertPdfToImages(any())).thenReturn(List.of(new byte[]{}));
        doAnswer(inv -> { job.setTotalPages(inv.getArgument(1)); return null; })
                .when(ingestionJobService).setTotalPages(eq("j3"), anyInt());

        when(ingestionJobService.findRetryablePageIndices(any(), eq(MAX_ATTEMPTS)))
                .thenReturn(new LinkedHashSet<>(List.of(0)));
        doAnswer(inv -> { ((BiConsumer<Integer, DocumentPage>) inv.getArgument(2)).accept(0, null); return null; })
                .when(kbDocumentChunker).processPages(any(), any(), any());
        when(ingestionJobService.countExhaustedFailures(any(), eq(MAX_ATTEMPTS))).thenReturn(0L);

        processor.processInternal("j3");

        verify(ingestionJobService).markInterrupted("j3");
        verify(ingestionJobService, never()).markCompleted(any());
        verify(ingestionJobService, never()).markFailed(any(), any());
    }

    @Test
    void processInternal_exceptionDuringPipeline_marksFailed() throws IOException {
        IngestionJob job = newJob("j4", "doc.pdf");
        when(ingestionJobService.findByUuid("j4")).thenReturn(Optional.of(job));
        when(fileService.load(any())).thenThrow(new IOException("disk error"));

        processor.processInternal("j4");

        verify(ingestionJobService).markFailed(eq("j4"), eq("disk error"));
    }

    @Test
    void processInternal_resumeAlreadyEmbedded_skipsReembedAndCompletes() {
        IngestionJob job = newJob("j5", "doc.pdf");
        job.setTotalPages(1);
        job.setChunksEmbedded(true);
        job.setKbDocumentUuid("kb-uuid");

        when(ingestionJobService.findByUuid("j5")).thenReturn(Optional.of(job));
        when(ingestionJobService.findRetryablePageIndices(any(), eq(MAX_ATTEMPTS)))
                .thenReturn(new LinkedHashSet<>());
        when(ingestionJobService.countExhaustedFailures(any(), eq(MAX_ATTEMPTS))).thenReturn(0L);

        processor.processInternal("j5");

        verify(ingestionJobService).markCompleted("j5");
        verify(ingestionJobService, never()).markChunksEmbedded(any());
        verify(embeddingModel, never()).call(any(EmbeddingRequest.class));
        verify(kbDocumentChunkRepository, never()).saveAll(any());
    }

    @Test
    void processInternal_tableChunkEmbedsSummaryNotContent() {
        IngestionJob job = newJob("j6", "doc.pdf");
        job.setTotalPages(1);
        job.setKbDocumentUuid("kb-uuid");

        when(ingestionJobService.findByUuid("j6")).thenReturn(Optional.of(job));
        when(ingestionJobService.findRetryablePageIndices(any(), eq(MAX_ATTEMPTS)))
                .thenReturn(new LinkedHashSet<>());
        when(ingestionJobService.countExhaustedFailures(any(), eq(MAX_ATTEMPTS))).thenReturn(0L);
        when(ingestionJobService.loadCompletedPages(any())).thenReturn(List.of(
                new DocumentPage(List.of(new Chunk("table", "| a |", "tbl-summary", false)))));
        when(kbDocumentChunker.mergePages(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kbDocumentRepository.findByUuid("kb-uuid")).thenReturn(Optional.of(kbDoc("kb-uuid")));
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(embedResponse(1));

        processor.processInternal("j6");

        ArgumentCaptor<EmbeddingRequest> captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingModel).call(captor.capture());
        assertThat(captor.getValue().getInstructions()).containsExactly("tbl-summary");
    }

    private IngestionJob newJob(String uuid, String name) {
        IngestionJob j = new IngestionJob();
        j.setUuid(uuid);
        j.setStatus(IngestionJobStatus.PENDING);
        j.setOriginalFileName(name);
        j.setFileSizeBytes(1024L);
        j.setStoredFileUuid("stored-uuid");
        return j;
    }

    private KBDocument kbDoc(String uuid) {
        KBDocument d = new KBDocument();
        d.setUuid(uuid);
        return d;
    }

    private EmbeddingResponse embedResponse(int n) {
        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            embeddings.add(new Embedding(new float[]{0.1f, 0.2f}, i));
        }
        return new EmbeddingResponse(embeddings);
    }
}
