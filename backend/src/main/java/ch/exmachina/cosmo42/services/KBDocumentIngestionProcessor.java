package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.entities.KBDocumentChunkType;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.fs.FileService;
import ch.exmachina.cosmo42.services.kb.FileConverter;
import ch.exmachina.cosmo42.services.kb.KBDocumentChunker;
import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class KBDocumentIngestionProcessor {

    IngestionJobService ingestionJobService;
    KBDocumentChunker kbDocumentChunker;
    FileConverter fileConverter;
    FileService fileService;
    KBDocumentRepository kbDocumentRepository;
    KBDocumentChunkRepository kbDocumentChunkRepository;
    EmbeddingModel embeddingModel;
    OpenAiEmbeddingOptions embeddingModelOptions;
    int maxPageAttempts;

    public KBDocumentIngestionProcessor(IngestionJobService ingestionJobService,
                                        KBDocumentChunker kbDocumentChunker,
                                        FileConverter fileConverter,
                                        FileService fileService,
                                        KBDocumentRepository kbDocumentRepository,
                                        KBDocumentChunkRepository kbDocumentChunkRepository,
                                        EmbeddingModel embeddingModel,
                                        OpenAiEmbeddingOptions embeddingModelOptions,
                                        @Value("${cosmo42.ingestion.max-page-attempts:3}") int maxPageAttempts) {
        this.ingestionJobService = ingestionJobService;
        this.kbDocumentChunker = kbDocumentChunker;
        this.fileConverter = fileConverter;
        this.fileService = fileService;
        this.kbDocumentRepository = kbDocumentRepository;
        this.kbDocumentChunkRepository = kbDocumentChunkRepository;
        this.embeddingModel = embeddingModel;
        this.embeddingModelOptions = embeddingModelOptions;
        this.maxPageAttempts = maxPageAttempts;
    }

    @Async("ingestionExecutor")
    public void processAsync(String jobUuid) {
        processInternal(jobUuid);
    }

    public void processInternal(String jobUuid) {
        IngestionJob job = ingestionJobService.findByUuid(jobUuid).orElseThrow();
        ingestionJobService.markProcessing(jobUuid);
        try {
            runPipeline(job);
        } catch (Exception e) {
            log.error("Ingestion job {} failed", jobUuid, e);
            ingestionJobService.markFailed(jobUuid, e.getMessage());
        }
    }

    private void runPipeline(IngestionJob job) throws Exception {
        job = chunkPendingPages(job);

        long exhausted = ingestionJobService.countExhaustedFailures(job, maxPageAttempts);
        if (exhausted > 0) {
            String msg = "Pages exceeded max attempts (" + maxPageAttempts + "): " + exhausted;
            log.error("Job {}: {}", job.getUuid(), msg);
            ingestionJobService.markFailed(job.getUuid(), msg);
            return;
        }

        int stillRetryable = ingestionJobService.findRetryablePageIndices(job, maxPageAttempts).size();
        if (stillRetryable > 0) {
            log.warn("Job {} has {} retryable failed page(s). Marking INTERRUPTED for next recovery cycle.",
                    job.getUuid(), stillRetryable);
            ingestionJobService.markInterrupted(job.getUuid());
            return;
        }

        if (!Boolean.TRUE.equals(job.getChunksEmbedded())) {
            job = ensureKbDocument(job);
            embedAndStore(job);
            ingestionJobService.markChunksEmbedded(job.getUuid());
        } else {
            log.info("Job {} embedding already done, skipping re-embed.", job.getUuid());
        }

        ingestionJobService.markCompleted(job.getUuid());
        log.info("Ingestion job {} completed successfully.", job.getUuid());
    }

    private IngestionJob chunkPendingPages(IngestionJob job) throws Exception {
        Set<Integer> retryable = job.getTotalPages() != null
                ? ingestionJobService.findRetryablePageIndices(job, maxPageAttempts)
                : null;

        if (retryable != null && retryable.isEmpty()) {
            return job;
        }

        byte[] rawBytes = fileService.load(job.getStoredFileUuid());
        byte[] pdf = fileConverter.convertSupportedFileToPdfFromBytes(rawBytes, job.getOriginalFileName());
        List<byte[]> pageImages = fileConverter.convertPdfToImages(pdf);

        if (job.getTotalPages() == null) {
            ingestionJobService.setTotalPages(job.getUuid(), pageImages.size());
            job = refresh(job.getUuid());
            retryable = ingestionJobService.findRetryablePageIndices(job, maxPageAttempts);
        }

        Map<Integer, DocumentPage> results = kbDocumentChunker.processPages(pageImages, retryable);
        IngestionJob jobRef = job;
        results.forEach((idx, page) -> ingestionJobService.savePageResult(jobRef, idx, page));

        return refresh(job.getUuid());
    }

    private IngestionJob ensureKbDocument(IngestionJob job) {
        if (job.getKbDocumentUuid() != null) return job;
        KBDocument doc = new KBDocument();
        doc.setUuid(job.getStoredFileUuid());
        doc.setFileName(job.getOriginalFileName());
        doc.setFileSize(job.getFileSizeBytes());
        doc.setCreationTimestamp(LocalDateTime.now());
        kbDocumentRepository.save(doc);
        ingestionJobService.setKbDocumentUuid(job.getUuid(), doc.getUuid());
        return refresh(job.getUuid());
    }

    private void embedAndStore(IngestionJob job) {
        KBDocument kbDocument = kbDocumentRepository.findByUuid(job.getKbDocumentUuid()).orElseThrow();
        List<DocumentPage> mergedPages = kbDocumentChunker.mergePages(ingestionJobService.loadCompletedPages(job));
        kbDocumentChunkRepository.deleteByKbDocument_Uuid(kbDocument.getUuid());
        embedAndSaveChunks(kbDocument, mergedPages);
    }

    private void embedAndSaveChunks(KBDocument kbDocument, List<DocumentPage> pages) {
        List<KBDocumentChunk> chunks = new ArrayList<>();
        List<String> toEmbed = new ArrayList<>();

        for (DocumentPage page : pages) {
            for (Chunk chunk : page.getChunks()) {
                KBDocumentChunk kbChunk = new KBDocumentChunk();
                kbChunk.setUuid(UUID.randomUUID().toString());
                kbChunk.setKbDocument(kbDocument);
                kbChunk.setType(KBDocumentChunkType.fromLabel(chunk.getType()));
                kbChunk.setContent(chunk.getContent());
                kbChunk.setSummary(chunk.getSummary());
                chunks.add(kbChunk);
                toEmbed.add(kbChunk.getType() == KBDocumentChunkType.TABLE
                        ? kbChunk.getSummary() : kbChunk.getContent());
            }
        }

        if (chunks.isEmpty()) return;

        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(toEmbed, embeddingModelOptions));
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(response.getResults().get(i).getOutput());
        }
        kbDocumentChunkRepository.saveAll(chunks);
        log.info("Saved {} chunks for document {}.", chunks.size(), kbDocument.getUuid());
    }

    private IngestionJob refresh(String jobUuid) {
        return ingestionJobService.findByUuid(jobUuid).orElseThrow();
    }
}
